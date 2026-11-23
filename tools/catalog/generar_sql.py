#!/usr/bin/env python3
"""Genera data.sql (perfil demo/H2) y docs/supabase/cartas.sql (Postgres/Supabase) desde docs/catalogo/cartas.csv.

Uso (desde la raiz del repo):   python tools/catalog/generar_sql.py
Edita el CSV (Excel vale: separador ';', UTF-8), ejecuta el script y vuelve a pegar cartas.sql en Supabase.
Formato de las columnas:
  targets : NONE | 1:ALLY | 1:ENEMY | 2:ENEMY | 1:ALLY;1:ENEMY   (cantidad:GRUPO; grupos soportados ALLY, ENEMY, ANY_UNIT)
  keywords: NONE | JUMPER | DISRUPTION | DISRUPTION,JUMPER        (solo JUMPER y DISRUPTION tienen logica hoy)
  effects : NONE | DISPARADOR:ACCION:valor[;...]
            disparadores: ON_PLACED ON_DESTROYED ON_DAMAGE_TAKEN ON_COMBAT_WIN ON_MOVEMENT_CONFIRMED
            acciones    : ENEMY_DAMAGE ALLY_BOOST SELF_BUFF CHARGE_BUFF READY_SELF DRAW  y HUNTER_MARK:dano:recompensa
  speed   : NOT_A_SPELL (unidades) | SLOW | FAST | INSTANT
"""
import csv, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'docs', 'catalogo', 'cartas.csv')
DATA_SQL = os.path.join(ROOT, 'TimewarpTCG.Back', 'src', 'main', 'resources', 'data.sql')
SUPA_SQL = os.path.join(ROOT, 'docs', 'supabase', 'cartas.sql')

FACTIONS = {'ANCIENT_EGYPT', 'ROMANS', 'STEAMPUNK', 'GLITCHPOP'}
KEYWORDS = {'JUMPER', 'DISRUPTION', 'GLORY', 'CHARGE'}
TRIGGERS = {'ON_PLACED', 'ON_DESTROYED', 'ON_DAMAGE_TAKEN', 'ON_COMBAT_WIN', 'ON_MOVEMENT_CONFIRMED'}
ACTIONS = {'ENEMY_DAMAGE', 'ALLY_BOOST', 'SELF_BUFF', 'CHARGE_BUFF', 'READY_SELF', 'DRAW', 'HUNTER_MARK'}
GROUPS = {'ALLY', 'ENEMY', 'ANY_UNIT'}


def q(s):
    return "'" + s.replace("'", "''") + "'"


def validate(r):
    errs = []
    if r['faction'] not in FACTIONS: errs.append('faccion')
    if r['type'] not in ('UNIT', 'SPELL'): errs.append('type')
    if r['type'] == 'UNIT' and r['speed'] != 'NOT_A_SPELL': errs.append('una unidad debe tener speed NOT_A_SPELL')
    if r['type'] == 'SPELL' and r['speed'] not in ('SLOW', 'FAST', 'INSTANT'): errs.append('speed de hechizo')
    if r['targets'] != 'NONE':
        for t in r['targets'].split(';'):
            m = re.fullmatch(r'(\d+):([A-Z_]+)', t)
            if not m or m.group(2) not in GROUPS: errs.append('target ' + t)
    if r['keywords'] != 'NONE':
        for k in r['keywords'].split(','):
            if k not in KEYWORDS: errs.append('keyword ' + k)
    if r['effects'] != 'NONE':
        for e in r['effects'].split(';'):
            p = e.split(':')
            if p[0] not in TRIGGERS or p[1] not in ACTIONS: errs.append('efecto ' + e)
    return errs


def main():
    with open(SRC, encoding='utf-8-sig', newline='') as f:
        rows = list(csv.DictReader(f, delimiter=';'))
    bad = False
    ids = set()
    for r in rows:
        e = validate(r)
        if r['id'] in ids: e.append('id repetido')
        ids.add(r['id'])
        if e:
            bad = True
            print('ERROR en', r['id'], e)
    if bad:
        sys.exit(1)

    def values(r):
        return '(' + ', '.join([q(r['id']), q(r['name']), q(r['description']), r['cost'], r['drive'], q(r['type']), q(r['speed']),
                                q(r['targets']), q(r['keywords']), q(r['effects']), q(r['faction'])]) + ')'

    cols = 'id, name, description, base_cost, base_drive, card_type, spell_speed, targets, keywords, effects, faction'
    body = ',\n'.join(values(r) for r in rows)

    with open(DATA_SQL, 'w', encoding='utf-8', newline='\n') as f:
        f.write('-- GENERADO por tools/catalog/generar_sql.py desde docs/catalogo/cartas.csv. No lo edites a mano.\n')
        f.write('-- Solo se ejecuta con el perfil demo (H2 en memoria).\n')
        f.write('DELETE FROM card_templates;\n\n')
        f.write('INSERT INTO card_templates (' + cols + ') VALUES\n' + body + ';\n')

    sets = ', '.join(f'{c} = EXCLUDED.{c}' for c in cols.split(', ') if c != 'id')
    with open(SUPA_SQL, 'w', encoding='utf-8', newline='\n') as f:
        f.write('-- GENERADO por tools/catalog/generar_sql.py desde docs/catalogo/cartas.csv.\n')
        f.write('-- Pegalo en Supabase > SQL Editor > Run. Es SEGURO repetirlo: inserta las cartas nuevas y actualiza las existentes\n')
        f.write('-- (no borra nada, asi que no rompe los mazos que ya usan estas cartas).\n')
        f.write('-- Requisito: haber arrancado el backend una vez contra Supabase para que Hibernate cree la tabla card_templates.\n\n')
        f.write('INSERT INTO card_templates (' + cols + ') VALUES\n' + body + '\nON CONFLICT (id) DO UPDATE SET ' + sets + ';\n')
    print(len(rows), 'cartas ->', os.path.relpath(DATA_SQL, ROOT), 'y', os.path.relpath(SUPA_SQL, ROOT))


main()
