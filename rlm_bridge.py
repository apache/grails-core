import sys
import json
import os
import logging
from rlm import RLM
from rlm.logger import RLMLogger
from rich.console import Console
from rich.logging import RichHandler

# Use rich console for stderr feedback
console = Console(stderr=True)

# Configure logging to use RichHandler on stderr with DEBUG level
logging.basicConfig(
    level=logging.DEBUG,
    format="%(message)s",
    datefmt="[%X]",
    handlers=[RichHandler(console=console, rich_tracebacks=True)]
)
logger = logging.getLogger("rlm_bridge")
logger.setLevel(logging.DEBUG)

def transform_paths(obj, root_path, to_relative=True):
    """Recursively transforms absolute paths to relative or vice versa."""
    if isinstance(obj, str):
        if to_relative:
            if obj.startswith(root_path):
                return os.path.relpath(obj, root_path)
        else:
            # Check if it's a relative path (not starting with / or C:\)
            if not os.path.isabs(obj) and (obj.startswith("./") or obj.startswith("../") or "/" in obj or "\\" in obj):
                return os.path.abspath(os.path.join(root_path, obj))
        return obj
    elif isinstance(obj, dict):
        return {k: transform_paths(v, root_path, to_relative) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [transform_paths(i, root_path, to_relative) for i in obj]
    return obj

import yaml

def save_state(project_id, state_data, workspace_root=None):
    """Saves the distilled reasoning state to disk as YAML."""
    base_dir = workspace_root if workspace_root else "."
    path = os.path.join(base_dir, ".mcp", "knowledge_base", f"{project_id}.yaml")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    
    root_path = workspace_root if workspace_root else os.getcwd()
    normalized_data = transform_paths(state_data, root_path, to_relative=True)
    
    with open(path, "w") as f:
        yaml.dump(normalized_data, f, sort_keys=False, default_flow_style=False)
    logger.info(f"💾 State saved to [bold blue]{path}[/]")

def load_state(project_id, workspace_root=None):
    """Loads previous reasoning state from YAML and expands relative paths."""
    base_dir = workspace_root if workspace_root else "."
    path = os.path.join(base_dir, ".mcp", "knowledge_base", f"{project_id}.yaml")
    if os.path.exists(path):
        with open(path, "r") as f:
            data = yaml.safe_load(f)
            root_path = workspace_root if workspace_root else os.getcwd()
            return transform_paths(data, root_path, to_relative=False)
    return {}

def run_rlm(prompt, model_name, base_url, environment="local", project_id=None, workspace_root=None):
    """
    Connects to a local Ollama server and runs RLM completion with persistence.
    """
    try:
        logger.info(f"🚀 Initializing RLM [bold cyan]{model_name}[/] | Project: {project_id}")
        
        # Load previous knowledge
        previous_knowledge = load_state(project_id, workspace_root) if project_id else {}
        
        # Build an enhanced prompt that includes previous reasoning
        enhanced_prompt = prompt
        if previous_knowledge:
            logger.info("📚 Loading existing knowledge into context...")
            enhanced_prompt = f"Previous Knowledge about this project: {json.dumps(previous_knowledge)}\n\nUser Query: {prompt}"

        rlm = RLM(
            backend="openai",
            backend_kwargs={
                "model_name": model_name,
                "base_url": base_url,
                "api_key": "ollama",
                "stream": True
            },
            environment=environment,
            verbose=True,
            logger=RLMLogger(log_dir="trajectories")
        )

        logger.info("🧠 [bold green]Starting recursive reasoning...[/]")
        result = rlm.completion(enhanced_prompt)

        # Post-process to extract new "Knowledge/Facts" for persistence
        if project_id:
            # We task a sub-LLM to distill the new reasoning into long-term facts
            logger.info("🧪 Distilling new reasoning into long-term knowledge...")
            distill_prompt = f"Distill the following reasoning into a set of permanent project facts (JSON format):\n{result.response}"
            distill_res = rlm.completion(distill_prompt) # recursive distillation
            try:
                new_facts = json.loads(distill_res.response)
                previous_knowledge.update(new_facts)
                save_state(project_id, previous_knowledge, workspace_root)
            except:
                logger.warning("⚠️ Failed to parse distilled facts as JSON, saving raw response.")
                previous_knowledge["latest_update"] = result.response
                save_state(project_id, previous_knowledge, workspace_root)

        logger.info("✅ [bold blue]Completion finished![/]")
        return {
            "status": "success",
            "response": result.response,
            "trajectory_log": getattr(result, "log_file", None)
        }
    except Exception as e:
        logger.error(f"❌ [bold red]RLM Error:[/] {str(e)}")
        return {
            "status": "error",
            "message": str(e)
        }

if __name__ == "__main__":
    try:
        # Read input from stdin
        logger.info("Bridge waiting for input...")
        input_raw = sys.stdin.read()
            
        if not input_raw:
            logger.error("No input received on stdin")
            sys.exit(1)
            
        logger.info(f"Received input length: {len(input_raw)}")
        input_data = json.loads(input_raw)
        
        prompt = input_data.get("prompt")
        model_name = input_data.get("model_name", "llama3.2:3b") # Default to lighter model
        base_url = input_data.get("base_url", "http://localhost:11434/v1")
        env = input_data.get("environment", "local")
        project_id = input_data.get("project_id")
        workspace_root = input_data.get("workspace_root") # New parameter

        # We wrap the RLM call in a 120s timeout to prevent zombie runs
        import signal
        def handler(signum, frame):
            raise TimeoutError("RLM completion timed out after 120 seconds")
        signal.signal(signal.SIGALRM, handler)
        signal.alarm(120)

        if not prompt:
            print(json.dumps({"status": "error", "message": "No prompt provided"}))
            sys.exit(1)

        result = run_rlm(prompt, model_name, base_url, env, project_id, workspace_root)
        signal.alarm(0) # Cancel alarm
        print(json.dumps(result))

    except Exception as e:
        logger.error(f"Bridge critical error: {str(e)}")
        print(json.dumps({"status": "error", "message": f"Bridge critical error: {str(e)}"}))
        sys.exit(1)
