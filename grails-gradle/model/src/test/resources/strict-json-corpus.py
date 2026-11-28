# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
"""Seeded fuzz of the strict JSON reader (StrictJson) against Python's json.

strict-json-corpus.tsv beside this file is `gen 2026 1200` judged by `judge`; StrictJsonSpec holds the
reader to every verdict in it. To fuzz more, generate inputs, judge them here and with the reader
(hex TAB shape TAB "OK " + StrictJson.write(value) or "REFUSED"), and compare.

  fuzz.py gen <seed> <count> <out.hex>            writes one input per line, as hex
  fuzz.py judge <in.hex> <out.tsv>                writes hex TAB shape TAB verdict, for both shapes
  fuzz.py compare <python.tsv> <groovy.tsv>       counts disagreements

Shapes: "array" is the file of arguments (an array of strings, at most 4096, none with U+0000);
"value" is any JSON value nested at most 4 deep, as a descriptor is read, with what the strict
reader refuses on purpose refused here too: a name twice in an object, NaN and Infinity.
"""
import json, random, sys, collections

FIXED = ['["a"]x', '["a",]', '[,"a"]', '["a" "b"]', '["a"]["b"]', "['a']", '["a\nb"]', '["a\tb"]', '["\\x"]', '["\\u12"]',
         '﻿["a"]', '["a"] // c', '[NaN]', '"abc"', '', '   ', '[', '["a"', '["a\\', '["a\\"', '["\\ud800"]', '["\\u0000"]',
         '[["a"]]', '[1]', '[null]', '[true]', '{"a":"b"}', '["a",\n"b"]', '[ ]', '["\\/"]', '["\u0001"]', '["a"],', '["a"]]',
         '[\'a\',"b"]', '["a";"b"]', '[a]', '["a" ]', '["\\uD83D\\uDE00"]', '["\\uDE00\\uD83D"]', '["a",,"b"]', '["a":"b"]',
         '[-]', '[tru', '[nul', '["\\', '["\\ud83d\\n"]', '["\\ud83d", "\\ude00"]', '["\x7f"]', '["é😀"]', '[]', ' [ "a" , "" ] ',
         '{"format":1,"class":"a.BCommand","name":"b","description":"Does b","target":"application"}',
         '{"format": 1, "class": "a.B", "name": "b", "options": [{"names": ["-f"], "arity": "0"}]}',
         '{"a":1,"a":2}', '{"a":{"a":1}}', '{"n":-0.5e+10,"t":true,"f":false,"z":null}', '{"n":01}', '{"n":1.}', '{"n":.5}',
         '{"n":1e}', '{"n":-}', '{"n":+1}', '{"n":0x10}', '{"n":Infinity}', '{"n":-Infinity}', '{"n":1E5}', '{}', '{ }',
         '{"a" :1 , "b": [ ] }', '[[[[1]]]]', '[[[[[1]]]]]', '{"a":{"b":{"c":{"d":1}}}}', '{"a":{"b":{"c":{"d":{}}}}}',
         '{"\\u0041":"\\u00e9\\n"}', '{"a":"b",}', '{,"a":"b"}', '{"a"}', '{"a":}', '{a:1}', "{'a':1}", '{"a":1}{"b":2}',
         'true', 'false', 'null', 'nul', 'truex', '0', '-0', '1 2', '"\\u00zz"', '"\\u+123"', '"\\u 123"', '"\\uff11234"']
ALPHABET = list('[]{}",:\\ \n\tabu0123456789dDfnrte-+.E/\'é😀') + ['\\u', '\\ud83d', '\\ude00', 'true', 'null', 'false', '"a"', ',"b"',
                                                                  '\\"', '\\\\', '{"a":', '[[', ']]', '\r', '\x00', '\x7f', '﻿', '１']


def gen(seed, count, out):
    rnd = random.Random(seed)
    with open(out, 'w') as f:
        for text in FIXED:
            f.write(text.encode('utf-8').hex() + '\n')
        for _ in range(count):
            chars = list(rnd.choice(FIXED))
            for _ in range(1 + rnd.randrange(4)):
                op = rnd.randrange(3)
                at = rnd.randrange(len(chars) + 1) if chars else 0
                if op == 0:
                    chars.insert(at, rnd.choice(ALPHABET))
                elif op == 1 and chars:
                    del chars[min(at, len(chars) - 1)]
                elif chars:
                    del chars[at:]
            raw = bytearray(''.join(chars).encode('utf-8'))
            if rnd.randrange(20) == 0 and raw:
                raw[rnd.randrange(len(raw))] = rnd.randrange(256)
            f.write(bytes(raw).hex() + '\n')


class Num(str):
    pass


def refuse_constant(_):
    raise ValueError('constant')


def pairs(items):
    seen = {}
    for name, value in items:
        if name in seen:
            raise ValueError('twice')
        seen[name] = value
    return seen


def depth(value):
    if isinstance(value, dict):
        return 1 + max([depth(v) for v in value.values()] + [0])
    if isinstance(value, list):
        return 1 + max([depth(v) for v in value] + [0])
    return 0


def check_text(value):
    if isinstance(value, Num):
        return
    if isinstance(value, str):
        value.encode('utf-8')
    elif isinstance(value, dict):
        for k, v in value.items():
            k.encode('utf-8')
            check_text(v)
    elif isinstance(value, list):
        for v in value:
            check_text(v)


def dump(value):
    if isinstance(value, Num):
        return str(value)
    if value is None:
        return 'null'
    if value is True:
        return 'true'
    if value is False:
        return 'false'
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=True)
    if isinstance(value, list):
        return '[' + ','.join(dump(v) for v in value) + ']'
    return '{' + ','.join(json.dumps(k, ensure_ascii=True) + ':' + dump(v) for k, v in value.items()) + '}'


def judge_value(raw):
    try:
        v = json.loads(raw.decode('utf-8'), parse_float=Num, parse_int=Num, parse_constant=refuse_constant, object_pairs_hook=pairs)
        if depth(v) > 4:
            return 'REFUSED'
        check_text(v)
        return 'OK ' + dump(v)
    except Exception:
        return 'REFUSED'


def judge_array(raw):
    try:
        v = json.loads(raw.decode('utf-8'))
        if not (isinstance(v, list) and all(isinstance(x, str) for x in v) and len(v) <= 4096):
            return 'REFUSED'
        if any('\x00' in x for x in v):
            return 'REFUSED'
        for x in v:
            x.encode('utf-8')
        return 'OK ' + json.dumps(v, ensure_ascii=True, separators=(',', ':'))
    except Exception:
        return 'REFUSED'


def judge(inp, out):
    with open(out, 'w') as f:
        for line in open(inp):
            h = line.strip()
            raw = bytes.fromhex(h)
            f.write(h + '\tarray\t' + judge_array(raw) + '\n')
            f.write(h + '\tvalue\t' + judge_value(raw) + '\n')


def compare(py, groovy):
    c = collections.Counter()
    shown = 0
    for a, b in zip(open(py), open(groovy)):
        ha, sa, va = a.rstrip('\n').split('\t', 2)
        hb, sb, vb = b.rstrip('\n').split('\t', 2)
        assert ha == hb and sa == sb
        if va == vb:
            kind = 'same ' + ('accepted' if va.startswith('OK') else 'refused')
        elif vb.startswith('THROWS'):
            kind = 'THROWS'
        elif vb.startswith('OK') and va == 'REFUSED':
            kind = 'groovy accepts, python refuses'
        elif vb == 'REFUSED':
            kind = 'groovy refuses, python accepts'
        else:
            kind = 'both accept, different value'
        c[sa + ': ' + kind] += 1
        if not kind.startswith('same') and shown < 12:
            shown += 1
            print(sa, kind, '|', repr(bytes.fromhex(ha))[:90], '| groovy:', vb[:70], '| python:', va[:70])
    for k in sorted(c):
        print(k, c[k])


if __name__ == '__main__':
    if sys.argv[1] == 'gen':
        gen(int(sys.argv[2]), int(sys.argv[3]), sys.argv[4])
    elif sys.argv[1] == 'judge':
        judge(sys.argv[2], sys.argv[3])
    else:
        compare(sys.argv[2], sys.argv[3])
