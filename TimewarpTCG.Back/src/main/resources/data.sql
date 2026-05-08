-- Borramos las cartas viejas por si reiniciamos el servidor para no tener duplicados
DELETE FROM card_templates;

-- Insertamos nuestro catálogo de cartas inicial
INSERT INTO card_templates (
    id, name, description, base_cost, base_drive, card_type, spell_speed, targets, keywords, effects, faction
) VALUES

-- 1. Carta Básica (Sin efectos, ideal para mazos Aggro)
('U_AE_01', 'Infantería Rápida', 'Unidad básica y veloz del imperio.',
 1, 2, 'UNIT', 'NOT_A_SPELL', 'NONE', 'NONE', 'NONE', 'ANCIENT_EGYPT'),

-- 2. Carta con Keyword (Tiene "JUMPER", se salta las reglas de movimiento)
('U_SP_01', 'Dron Explorador', 'Puede moverse libremente de un campo a otro.',
 2, 1, 'UNIT', 'NOT_A_SPELL', 'NONE', 'JUMPER', 'NONE', 'STEAMPUNK'),

-- 3. Carta con Efecto al Entrar (Requiere elegir a 1 Aliado para darle el Buff)
('U_RM_01', 'Centurión de Vanguardia', 'Al entrar en juego, da +2 de Drive a un aliado.',
 3, 4, 'UNIT', 'NOT_A_SPELL', '1:ALLY', 'NONE', 'ON_PLACED:ALLY_BOOST:2', 'ROMANS'),

-- 4. Carta con Efecto Reactivo (Se enfurece al recibir daño)
('U_GP_01', 'Ciborg Mutante', 'Si sobrevive al daño, gana +2 de Drive.',
 4, 5, 'UNIT', 'NOT_A_SPELL', 'NONE', 'NONE', 'ON_DAMAGE_TAKEN:SELF_BUFF:2', 'GLITCHPOP'),

-- 5. Hechizo Rápido de Daño (Requiere elegir a 1 Enemigo para dañarlo)
('S_GP_01', 'Virus Táctico', 'Inflige 3 de daño a una unidad enemiga.',
 2, 0, 'SPELL', 'FAST', '1:ENEMY', 'NONE', 'ON_PLACED:ENEMY_DAMAGE:3', 'GLITCHPOP');