#!/usr/bin/env python3
"""Genera docs/catalogo/CARTAS_EXPLICADAS.md: cada carta del catalogo con su receta (targets / keywords / effects)
traducida a una frase en castellano. Sirve para recordar que hace cada cadena de texto de la tabla card_templates.

Uso (desde la raiz del repo):   python tools/catalog/explicar_catalogo.py
Se vuelve a ejecutar cada vez que cambies docs/catalogo/cartas.csv (junto con generar_sql.py).
Las reglas que explica son las de docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md.
"""
import csv, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'docs', 'catalogo', 'cartas.csv')
OUT = os.path.join(ROOT, 'docs', 'catalogo', 'CARTAS_EXPLICADAS.md')

FACCION = {'ANCIENT_EGYPT': 'Antiguo Egipto', 'ROMANS': 'Romanos', 'STEAMPUNK': 'Steampunk', 'GLITCHPOP': 'Glitchpop'}
GRUPO = {'ALLY': 'aliada', 'ENEMY': 'enemiga', 'ANY_UNIT': 'cualquiera'}
KEYWORD = {'JUMPER': 'Salto: puede pasar de un carril a otro',
           'DISRUPTION': 'Disrupción: puede bajar directa a un carril disputado',
           'GLORY': '(GLORY: sin lógica, ver READY_SELF)', 'CHARGE': '(CHARGE: sin lógica, ver CHARGE_BUFF)'}
VELOCIDAD = {'SLOW': 'Lenta', 'FAST': 'Rápida', 'INSTANT': 'Instantánea'}


def plural(n, uno, varios):
    return f'{n} {uno if n == 1 else varios}'


def explica_targets(t):
    if t == 'NONE':
        return 'no pide objetivos'
    partes = []
    for req in t.split(';'):
        n, g = req.split(':')
        n = int(n)
        partes.append(f"{n} unidad{'es' if n != 1 else ''} {GRUPO[g]}{'s' if n != 1 and g != 'ANY_UNIT' else ''}")
    return 'elige ' + ' y '.join(partes)


def explica_efecto(regla, es_hechizo):
    p = regla.split(':')
    trig, acc = p[0], p[1]
    cuando = {'ON_PLACED': 'Al resolverse' if es_hechizo else 'Al entrar en juego',
              'ON_DESTROYED': 'Al ser destruida',
              'ON_DAMAGE_TAKEN': 'Al recibir daño (solo si sobrevive)',
              'ON_COMBAT_WIN': 'Al ganar un combate',
              'ON_MOVEMENT_CONFIRMED': 'Al confirmar su movimiento'}[trig]
    if acc == 'ENEMY_DAMAGE':
        que = f'hace {p[2]} de daño a cada objetivo enemigo'
    elif acc == 'ALLY_BOOST':
        que = f'da +{p[2]} de Drive a cada objetivo aliado'
    elif acc == 'SELF_BUFF':
        que = f'gana +{p[2]} de Drive'
    elif acc == 'CHARGE_BUFF':
        que = f'gana +{p[2]} de Drive si acaba en un carril'
    elif acc == 'READY_SELF':
        que = 'se recupera (vuelve a poder moverse)'
    elif acc == 'DRAW':
        que = f"roba {plural(int(p[2]), 'carta', 'cartas')}"
    elif acc == 'HUNTER_MARK':
        que = f'hace {p[2]} de daño al objetivo; cuando ese objetivo muere (ahora o más tarde) gana +{p[3]} de Drive'
    else:
        que = f'(acción {acc} desconocida)'
    return f'{cuando}: {que}'


def main():
    filas = list(csv.DictReader(open(SRC, encoding='utf-8-sig'), delimiter=';'))
    out = ['# Catálogo explicado, carta por carta', '',
           '> Generado con `python tools/catalog/explicar_catalogo.py` a partir de `docs/catalogo/cartas.csv`. **No lo edites a mano.**',
           '> Qué significa cada trozo de las columnas `targets`, `keywords` y `effects`: `docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md`.', '']
    for fac in ['ANCIENT_EGYPT', 'ROMANS', 'STEAMPUNK', 'GLITCHPOP']:
        out += [f'## {FACCION[fac]}', '',
                '| id | carta | coste | Drive | tipo | `targets` | `keywords` | `effects` | en palabras |',
                '|---|---|---|---|---|---|---|---|---|']
        for r in [x for x in filas if x['faction'] == fac]:
            es_h = r['type'] == 'SPELL'
            palabras = []
            if r['targets'] != 'NONE':
                palabras.append(explica_targets(r['targets']) + '.')
            if r['keywords'] != 'NONE':
                palabras.append('; '.join(KEYWORD[k] for k in r['keywords'].split(',')) + '.')
            if r['effects'] != 'NONE':
                palabras.append(' '.join(explica_efecto(x, es_h) + '.' for x in r['effects'].split(';')))
            if not palabras:
                palabras.append('Sin habilidades: solo cuerpo.' if not es_h else 'Sin efecto.')
            tipo = f"Hechizo {VELOCIDAD[r['speed']]}" if es_h else 'Unidad'
            drive = '—' if es_h else r['drive']
            out.append(f"| {r['id']} | **{r['name']}** | {r['cost']} | {drive} | {tipo} | `{r['targets']}` | `{r['keywords']}` | `{r['effects']}` | {' '.join(palabras)} |")
        out.append('')
    open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
    print('escrito', os.path.relpath(OUT, ROOT), '-', len(filas), 'cartas')


if __name__ == '__main__':
    main()
