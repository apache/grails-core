package org.grails.datastore.gorm.neo4j.parsers;

import java.util.*;


/**
 * <p>
 * This class is part of the Java Tools (see http://mpii.de/yago-naga/javatools).
 * It is licensed under the Creative Commons Attribution License
 * (see http://creativecommons.org/licenses/by/3.0) by
 * the YAGO-NAGA team (see http://mpii.de/yago-naga).
 * </p>
 * <p>
 * The PlingStemmer stems an English noun (plural or singular) to its singular
 * form. It deals with "firemen"-&gt;"fireman", it knows Greek stuff like
 * "appendices"-&gt;"appendix" and yes, it was a lot of work to compile these exceptions.
 * Examples:
 *
 * </p>
 * <PRE>
 * System.out.println(PlingStemmer.stem("boy"));
 * ----&gt; boy
 * System.out.println(PlingStemmer.stem("boys"));
 * ----&gt; boy
 * System.out.println(PlingStemmer.stem("biophysics"));
 * ----&gt;  biophysics
 * System.out.println(PlingStemmer.stem("automata"));
 * ----&gt; automaton
 * System.out.println(PlingStemmer.stem("genus"));
 * ----&gt; genus
 * System.out.println(PlingStemmer.stem("emus"));
 * ----&gt; emu
 * </PRE><P>
 * <p>
 * There are a number of word forms that can either be plural or singular.
 * Examples include "physics" (the science or the plural of "physic" (the
 * medicine)), "quarters" (the housing or the plural of "quarter" (1/4))
 * or "people" (the singular of "peoples" or the plural of "person"). In
 * these cases, the stemmer assumes the word is a plural form and returns
 * the singular form. The methods isPlural, isSingular and isPluralAndSingular
 * can be used to differentiate the cases.<P>
 * </p>
 * <p>
 * It cannot be guaranteed that the stemmer correctly stems a plural word
 * or correctly ignores a singular word -- let alone that it treats an
 * ambiguous word form in the way expected by the user.<P>
 * </p>
 * <p>
 * The PlingStemmer uses material from <A HREF=http://wordnet.princeton.edu/>WordNet</A>.
 * </p>
 */
public class PlingStemmer {

    /**
     * Tells whether a word form is plural. This method just checks whether the
     * stem method alters the word
     */
    public static boolean isPlural(String s) {
        return (!s.equals(stem(s)));
    }

    /**
     * Tells whether a word form is singular. Note that a word can be both plural and singular
     */
    public static boolean isSingular(String s) {
        return (singAndPlur.contains(s.toLowerCase()) || !isPlural(s));
    }

    /**
     * Tells whether a word form is the singular form of one word and at
     * the same time the plural form of another.
     */
    public static boolean isSingularAndPlural(String s) {
        return (singAndPlur.contains(s.toLowerCase()));
    }

    /**
     * Stems an English noun
     */
    public static String stem(String s) {
        String stem = s;

        // Handle irregular ones
        String irreg = irregular.get(s);
        if (irreg != null) return (stem = irreg);

        // -on to -a
        if (categoryON_A.contains(s)) return (stem = cut(s, "a") + "on");

        // -um to -a
        if (categoryUM_A.contains(s)) return (stem = cut(s, "a") + "um");

        // -x to -ices
        if (categoryIX_ICES.contains(s)) return (stem = cut(s, "ices") + "ix");

        // -o to -i
        if (categoryO_I.contains(s)) return (stem = cut(s, "i") + "o");

        // -se to ses
        if (categorySE_SES.contains(s)) return (stem = cut(s, "s"));

        // -is to -es
        if (categoryIS_ES.contains(s) || s.endsWith("theses")) return (stem = cut(s, "es") + "is");

        // -us to -i
        if (categoryUS_I.contains(s)) return (stem = cut(s, "i") + "us");
        //Wrong plural
        if (s.endsWith("uses") && (categoryUS_I.contains(cut(s, "uses") + "i") ||
                s.equals("genuses") || s.equals("corpuses"))) return (stem = cut(s, "es"));

        // -ex to -ices
        if (categoryEX_ICES.contains(s)) return (stem = cut(s, "ices") + "ex");

        // Words that do not inflect in the plural
        if (s.endsWith("ois") || s.endsWith("itis") || category00.contains(s) || categoryICS.contains(s))
            return (stem = s);

        // -en to -ina
        // No other common words end in -ina
        if (s.endsWith("ina")) return (stem = cut(s, "en"));

        // -a to -ae
        // No other common words end in -ae
        if (s.endsWith("ae")) return (stem = cut(s, "e"));

        // -a to -ata
        // No other common words end in -ata
        if (s.endsWith("ata")) return (stem = cut(s, "ta"));

        // trix to -trices
        // No common word ends with -trice(s)
        if (s.endsWith("trices")) return (stem = cut(s, "trices") + "trix");

        // -us to -us
        //No other common word ends in -us, except for false plurals of French words
        //Catch words that are not latin or known to end in -u
        if (s.endsWith("us") && !s.endsWith("eaus") && !s.endsWith("ieus") && !noLatin(s)
                && !categoryU_US.contains(s)) return (stem = s);

        // -tooth to -teeth
        // -goose to -geese
        // -foot to -feet
        // -zoon to -zoa
        //No other common words end with the indicated suffixes
        if (s.endsWith("teeth")) return (stem = cut(s, "teeth") + "tooth");
        if (s.endsWith("geese")) return (stem = cut(s, "geese") + "goose");
        if (s.endsWith("feet")) return (stem = cut(s, "feet") + "foot");
        if (s.endsWith("zoa")) return (stem = cut(s, "zoa") + "zoon");

        // -eau to -eaux
        //No other common words end in eaux
        if (s.endsWith("eaux")) return (stem = cut(s, "x"));

        // -ieu to -ieux
        //No other common words end in ieux
        if (s.endsWith("ieux")) return (stem = cut(s, "x"));

        // -nx to -nges
        // Pay attention not to kill words ending in -nge with plural -nges
        // Take only Greek words (works fine, only a handfull of exceptions)
        if (s.endsWith("nges") && greek(s)) return (stem = cut(s, "nges") + "nx");

        // -[sc]h to -[sc]hes
        //No other common word ends with "shes", "ches" or "she(s)"
        //Quite a lot end with "che(s)", filter them out
        if (s.endsWith("shes") || s.endsWith("ches") && !categoryCHE_CHES.contains(s)) return (stem = cut(s, "es"));

        // -ss to -sses
        // No other common singular word ends with "sses"
        // Filter out those ending in "sse(s)"
        if (s.endsWith("sses") && !categorySSE_SSES.contains(s) && !s.endsWith("mousses")) return (stem = cut(s, "es"));

        // -x to -xes
        // No other common word ends with "xe(s)" except for "axe"
        if (s.endsWith("xes") && !s.equals("axes")) return (stem = cut(s, "es"));

        // -[nlw]ife to -[nlw]ives
        //No other common word ends with "[nlw]ive(s)" except for olive
        if (s.endsWith("nives") || s.endsWith("lives") && !s.endsWith("olives") ||
                s.endsWith("wives")) return (stem = cut(s, "ves") + "fe");

        // -[aeo]lf to -ves  exceptions: valve, solve
        // -[^d]eaf to -ves  exceptions: heave, weave
        // -arf to -ves      no exception
        if (s.endsWith("alves") && !s.endsWith("valves") ||
                s.endsWith("olves") && !s.endsWith("solves") ||
                s.endsWith("eaves") && !s.endsWith("heaves") && !s.endsWith("weaves") ||
                s.endsWith("arves")) return (stem = cut(s, "ves") + "f");

        // -y to -ies
        // -ies is very uncommon as a singular suffix
        // but -ie is quite common, filter them out
        if (s.endsWith("ies") && !categoryIE_IES.contains(s)) return (stem = cut(s, "ies") + "y");

        // -o to -oes
        // Some words end with -oe, so don't kill the "e"
        if (s.endsWith("oes") && !categoryOE_OES.contains(s)) return (stem = cut(s, "es"));

        // -s to -ses
        // -z to -zes
        // no words end with "-ses" or "-zes" in singular
        if (s.endsWith("ses") || s.endsWith("zes")) return (stem = cut(s, "es"));

        // - to -s
        if (s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("is")) return (stem = cut(s, "s"));

        return stem;
    }

    /**
     * Cuts a suffix from a string (that is the number of chars given by the suffix)
     */
    private static String cut(String s, String suffix) {
        return (s.substring(0, s.length() - suffix.length()));
    }

    /**
     * Returns true if a word is probably Greek
     */
    private static boolean greek(String s) {
        return (s.indexOf("ph") > 0 || s.indexOf('y') > 0 && s.endsWith("nges"));
    }

    /**
     * Returns true if a word is probably not Latin
     */
    private static boolean noLatin(String s) {
        return (s.indexOf('h') > 0 || s.indexOf('j') > 0 || s.indexOf('k') > 0 ||
                s.indexOf('w') > 0 || s.indexOf('y') > 0 || s.indexOf('z') > 0 ||
                s.indexOf("ou") > 0 || s.indexOf("sh") > 0 || s.indexOf("ch") > 0 ||
                s.endsWith("aus"));
    }

    /**
     * Words that end in "-se" in their plural forms (like "nurse" etc.)
     */
    private static Set<String> categorySE_SES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "nurses",
            "cruises",
            "premises",
            "houses",
            "courses",
            "cases"
    )));

    /**
     * Words that do not have a distinct plural form (like "atlas" etc.)
     */
    private static Set<String> category00 = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "alias",
            "asbestos",
            "atlas",
            "barracks",
            "bathos",
            "bias",
            "breeches",
            "britches",
            "canvas",
            "chaos",
            "clippers",
            "contretemps",
            "corps",
            "cosmos",
            "crossroads",
            "diabetes",
            "ethos",
            "gallows",
            "gas",
            "graffiti",
            "headquarters",
            "herpes",
            "high-jinks",
            "innings",
            "jackanapes",
            "lens",
            "means",
            "measles",
            "mews",
            "mumps",
            "news",
            "pathos",
            "pincers",
            "pliers",
            "proceedings",
            "rabies",
            "rhinoceros",
            "sassafras",
            "scissors",
            "series",
            "shears",
            "species",
            "tuna"
    )));

    /**
     * Words that change from "-um" to "-a" (like "curriculum" etc.), listed in their plural forms
     */
    private static Set<String> categoryUM_A = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "addenda",
            "agenda",
            "aquaria",
            "bacteria",
            "candelabra",
            "compendia",
            "consortia",
            "crania",
            "curricula",
            "data",
            "desiderata",
            "dicta",
            "emporia",
            "enconia",
            "errata",
            "extrema",
            "gymnasia",
            "honoraria",
            "interregna",
            "lustra",
            "maxima",
            "media",
            "memoranda",
            "millenia",
            "minima",
            "momenta",
            "optima",
            "ova",
            "phyla",
            "quanta",
            "rostra",
            "spectra",
            "specula",
            "stadia",
            "strata",
            "symposia",
            "trapezia",
            "ultimata",
            "vacua",
            "vela"
    )));

    /**
     * Words that change from "-on" to "-a" (like "phenomenon" etc.), listed in their plural forms
     */
    private static Set<String> categoryON_A = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "aphelia",
            "asyndeta",
            "automata",
            "criteria",
            "hyperbata",
            "noumena",
            "organa",
            "perihelia",
            "phenomena",
            "prolegomena"
    )));

    /**
     * Words that change from "-o" to "-i" (like "libretto" etc.), listed in their plural forms
     */
    private static Set<String> categoryO_I = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "alti",
            "bassi",
            "canti",
            "contralti",
            "crescendi",
            "libretti",
            "soli",
            "soprani",
            "tempi",
            "virtuosi"
    )));

    /**
     * Words that change from "-us" to "-i" (like "fungus" etc.), listed in their plural forms
     */
    private static Set<String> categoryUS_I = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "alumni",
            "bacilli",
            "cacti",
            "foci",
            "fungi",
            "genii",
            "hippopotami",
            "incubi",
            "nimbi",
            "nuclei",
            "nucleoli",
            "octopi",
            "radii",
            "stimuli",
            "styli",
            "succubi",
            "syllabi",
            "termini",
            "tori",
            "umbilici",
            "uteri"
    )));

    /**
     * Words that change from "-ix" to "-ices" (like "appendix" etc.), listed in their plural forms
     */
    private static Set<String> categoryIX_ICES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "appendices",
            "cervices"
    )));

    /**
     * Words that change from "-is" to "-es" (like "axis" etc.), listed in their plural forms
     */
    private static Set<String> categoryIS_ES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            // plus everybody ending in theses
            "analyses",
            "axes",
            "bases",
            "crises",
            "diagnoses",
            "ellipses",
            "emphases",
            "neuroses",
            "oases",
            "paralyses",
            "synopses"
    )));

    /**
     * Words that change from "-oe" to "-oes" (like "toe" etc.), listed in their plural forms
     */
    private static Set<String> categoryOE_OES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "aloes",
            "backhoes",
            "beroes",
            "canoes",
            "chigoes",
            "cohoes",
            "does",
            "felloes",
            "floes",
            "foes",
            "gumshoes",
            "hammertoes",
            "hoes",
            "hoopoes",
            "horseshoes",
            "leucothoes",
            "mahoes",
            "mistletoes",
            "oboes",
            "overshoes",
            "pahoehoes",
            "pekoes",
            "roes",
            "shoes",
            "sloes",
            "snowshoes",
            "throes",
            "tic-tac-toes",
            "tick-tack-toes",
            "ticktacktoes",
            "tiptoes",
            "tit-tat-toes",
            "toes",
            "toetoes",
            "tuckahoes",
            "woes"
    )));

    /**
     * Words that change from "-ex" to "-ices" (like "index" etc.), listed in their plural forms
     */
    private static Set<String> categoryEX_ICES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "apices",
            "codices",
            "cortices",
            "indices",
            "latices",
            "murices",
            "pontifices",
            "silices",
            "simplices",
            "vertices",
            "vortices"
    )));

    /**
     * Words that change from "-u" to "-us" (like "emu" etc.), listed in their plural forms
     */
    private static Set<String> categoryU_US = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "apercus",
            "barbus",
            "cornus",
            "ecrus",
            "emus",
            "fondus",
            "gnus",
            "iglus",
            "mus",
            "nandus",
            "napus",
            "poilus",
            "quipus",
            "snafus",
            "tabus",
            "tamandus",
            "tatus",
            "timucus",
            "tiramisus",
            "tofus",
            "tutus"
    )));

    /**
     * Words that change from "-sse" to "-sses" (like "finesse" etc.), listed in their plural forms
     */
    private static Set<String> categorySSE_SSES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            //plus those ending in mousse
            "bouillabaisses",
            "coulisses",
            "crevasses",
            "crosses",
            "cuisses",
            "demitasses",
            "ecrevisses",
            "fesses",
            "finesses",
            "fosses",
            "impasses",
            "lacrosses",
            "largesses",
            "masses",
            "noblesses",
            "palliasses",
            "pelisses",
            "politesses",
            "posses",
            "tasses",
            "wrasses"
    )));

    /**
     * Words that change from "-che" to "-ches" (like "brioche" etc.), listed in their plural forms
     */
    private static Set<String> categoryCHE_CHES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "adrenarches",
            "attaches",
            "avalanches",
            "barouches",
            "brioches",
            "caches",
            "caleches",
            "caroches",
            "cartouches",
            "cliches",
            "cloches",
            "creches",
            "demarches",
            "douches",
            "gouaches",
            "guilloches",
            "headaches",
            "heartaches",
            "huaraches",
            "menarches",
            "microfiches",
            "moustaches",
            "mustaches",
            "niches",
            "panaches",
            "panoches",
            "pastiches",
            "penuches",
            "pinches",
            "postiches",
            "psyches",
            "quiches",
            "schottisches",
            "seiches",
            "soutaches",
            "synecdoches",
            "thelarches",
            "troches"
    )));

    /**
     * Words that end with "-ics" and do not exist as nouns without the 's' (like "aerobics" etc.)
     */
    private static Set<String> categoryICS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "aerobatics",
            "aerobics",
            "aerodynamics",
            "aeromechanics",
            "aeronautics",
            "alphanumerics",
            "animatronics",
            "apologetics",
            "architectonics",
            "astrodynamics",
            "astronautics",
            "astrophysics",
            "athletics",
            "atmospherics",
            "autogenics",
            "avionics",
            "ballistics",
            "bibliotics",
            "bioethics",
            "biometrics",
            "bionics",
            "bionomics",
            "biophysics",
            "biosystematics",
            "cacogenics",
            "calisthenics",
            "callisthenics",
            "catoptrics",
            "civics",
            "cladistics",
            "cryogenics",
            "cryonics",
            "cryptanalytics",
            "cybernetics",
            "cytoarchitectonics",
            "cytogenetics",
            "diagnostics",
            "dietetics",
            "dramatics",
            "dysgenics",
            "econometrics",
            "economics",
            "electromagnetics",
            "electronics",
            "electrostatics",
            "endodontics",
            "enterics",
            "ergonomics",
            "eugenics",
            "eurhythmics",
            "eurythmics",
            "exodontics",
            "fibreoptics",
            "futuristics",
            "genetics",
            "genomics",
            "geographics",
            "geophysics",
            "geopolitics",
            "geriatrics",
            "glyptics",
            "graphics",
            "gymnastics",
            "hermeneutics",
            "histrionics",
            "homiletics",
            "hydraulics",
            "hydrodynamics",
            "hydrokinetics",
            "hydroponics",
            "hydrostatics",
            "hygienics",
            "informatics",
            "kinematics",
            "kinesthetics",
            "kinetics",
            "lexicostatistics",
            "linguistics",
            "lithoglyptics",
            "liturgics",
            "logistics",
            "macrobiotics",
            "macroeconomics",
            "magnetics",
            "magnetohydrodynamics",
            "mathematics",
            "metamathematics",
            "metaphysics",
            "microeconomics",
            "microelectronics",
            "mnemonics",
            "morphophonemics",
            "neuroethics",
            "neurolinguistics",
            "nucleonics",
            "numismatics",
            "obstetrics",
            "onomastics",
            "orthodontics",
            "orthopaedics",
            "orthopedics",
            "orthoptics",
            "paediatrics",
            "patristics",
            "patristics",
            "pedagogics",
            "pediatrics",
            "periodontics",
            "pharmaceutics",
            "pharmacogenetics",
            "pharmacokinetics",
            "phonemics",
            "phonetics",
            "phonics",
            "photomechanics",
            "physiatrics",
            "pneumatics",
            "poetics",
            "politics",
            "pragmatics",
            "prosthetics",
            "prosthodontics",
            "proteomics",
            "proxemics",
            "psycholinguistics",
            "psychometrics",
            "psychonomics",
            "psychophysics",
            "psychotherapeutics",
            "robotics",
            "semantics",
            "semiotics",
            "semitropics",
            "sociolinguistics",
            "stemmatics",
            "strategics",
            "subtropics",
            "systematics",
            "tectonics",
            "telerobotics",
            "therapeutics",
            "thermionics",
            "thermodynamics",
            "thermostatics"
    )));

    /**
     * Words that change from "-ie" to "-ies" (like "auntie" etc.), listed in their plural forms
     */
    private static Set<String> categoryIE_IES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "aeries",
            "anomies",
            "aunties",
            "baddies",
            "beanies",
            "birdies",
            "boccies",
            "bogies",
            "bolshies",
            "bombies",
            "bonhomies",
            "bonxies",
            "booboisies",
            "boogies",
            "boogie-woogies",
            "bookies",
            "booties",
            "bosies",
            "bourgeoisies",
            "brasseries",
            "brassies",
            "brownies",
            "budgies",
            "byrnies",
            "caddies",
            "calories",
            "camaraderies",
            "capercaillies",
            "capercailzies",
            "cassies",
            "catties",
            "causeries",
            "charcuteries",
            "chinoiseries",
            "collies",
            "commies",
            "cookies",
            "coolies",
            "coonties",
            "cooties",
            "corries",
            "coteries",
            "cowpies",
            "cowries",
            "cozies",
            "crappies",
            "crossties",
            "curies",
            "dachsies",
            "darkies",
            "dassies",
            "dearies",
            "dickies",
            "dies",
            "dixies",
            "doggies",
            "dogies",
            "dominies",
            "dovekies",
            "eyries",
            "faeries",
            "falsies",
            "floozies",
            "folies",
            "foodies",
            "freebies",
            "gaucheries",
            "gendarmeries",
            "genies",
            "ghillies",
            "gillies",
            "goalies",
            "goonies",
            "grannies",
            "grotesqueries",
            "groupies",
            "hankies",
            "hippies",
            "hoagies",
            "honkies",
            "hymies",
            "indies",
            "junkies",
            "kelpies",
            "kilocalories",
            "knobkerries",
            "koppies",
            "kylies",
            "laddies",
            "lassies",
            "lies",
            "lingeries",
            "magpies",
            "magpies",
            "marqueteries",
            "mashies",
            "mealies",
            "meanies",
            "menageries",
            "millicuries",
            "mollies",
            "facts1",
            "moxies",
            "neckties",
            "newbies",
            "nighties",
            "nookies",
            "oldies",
            "organdies",
            "panties",
            "parqueteries",
            "passementeries",
            "patisseries",
            "pies",
            "pinkies",
            "pixies",
            "porkpies",
            "potpies",
            "prairies",
            "preemies",
            "premies",
            "punkies",
            "pyxies",
            "quickies",
            "ramies",
            "reveries",
            "rookies",
            "rotisseries",
            "scrapies",
            "sharpies",
            "smoothies",
            "softies",
            "stoolies",
            "stymies",
            "swaggies",
            "sweeties",
            "talkies",
            "techies",
            "ties",
            "tooshies",
            "toughies",
            "townies",
            "veggies",
            "walkie-talkies",
            "wedgies",
            "weenies",
            "weirdies",
            "yardies",
            "yuppies",
            "zombies"
    )));

    /**
     * Maps irregular Germanic English plural nouns to their singular form
     */
    private static Map<String, String> irregular = Collections.unmodifiableMap(new HashMap<String, String>() {
                                                                                   {
                                                                                       put("beefs", "beef");
                                                                                       put("beeves", "beef");
                                                                                       put("brethren", "brother");
                                                                                       put("busses", "bus");
                                                                                       put("cattle", "cattlebeast");
                                                                                       put("children", "child");
                                                                                       put("corpora", "corpus");
                                                                                       put("ephemerides", "ephemeris");
                                                                                       put("firemen", "fireman");
                                                                                       put("genera", "genus");
                                                                                       put("genies", "genie");
                                                                                       put("genii", "genie");
                                                                                       put("kine", "cow");
                                                                                       put("lice", "louse");
                                                                                       put("men", "man");
                                                                                       put("mice", "mouse");
                                                                                       put("mongooses", "mongoose");
                                                                                       put("monies", "money");
                                                                                       put("mythoi", "mythos");
                                                                                       put("octopodes", "octopus");
                                                                                       put("octopuses", "octopus");
                                                                                       put("oxen", "ox");
                                                                                       put("people", "person");
                                                                                       put("soliloquies", "soliloquy");
                                                                                       put("throes", "throes");
                                                                                       put("trilbys", "trilby");
                                                                                       put("women", "woman");
                                                                                   }
                                                                               }
    );

    /**
     * Contains word forms that can either be plural or singular
     */
    private static Set<String> singAndPlur = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "acoustics",
            "aestetics",
            "aquatics",
            "basics",
            "ceramics",
            "classics",
            "cosmetics",
            "dermatoglyphics",
            "dialectics",
            "dynamics",
            "esthetics",
            "ethics",
            "harmonics",
            "heroics",
            "isometrics",
            "mechanics",
            "metrics",
            "statistics",
            "optic",
            "people",
            "physics",
            "polemics",
            "premises",
            "propaedeutics",
            "pyrotechnics",
            "quadratics",
            "quarters",
            "statistics",
            "tactics",
            "tropics"
    )));
}
