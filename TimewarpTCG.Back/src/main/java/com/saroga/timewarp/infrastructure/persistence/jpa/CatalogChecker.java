package com.saroga.timewarp.infrastructure.persistence.jpa;

import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.CardTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Revisa el catalogo (tabla card_templates) al arrancar y lo cuenta en la consola, sin impedir el arranque.
 *
 * POR QUE: las columnas "effects", "targets" y "keywords" son TEXTO que se interpreta al empezar una partida. Un typo
 * (por ejemplo ON_PLACE en vez de ON_PLACED) no da error: el efecto simplemente no se dispara nunca; y otros
 * (una keyword mal escrita, un numero que no lo es) hacen fallar la partida en cuanto sale esa carta.
 * Las reglas son las de docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md.
 */
@Component
public class CatalogChecker implements ApplicationRunner {

    /** Disparadores que el motor realmente dispara (TriggeredEffect). Cualquier otro nombre no hace nada. */
    static final Set<String> TRIGGERS = Set.of("ON_PLACED", "ON_DESTROYED", "ON_DAMAGE_TAKEN", "ON_COMBAT_WIN", "ON_MOVEMENT_CONFIRMED");
    /** Acciones registradas en EffectFactory. */
    static final Set<String> ACTIONS = Set.of("ENEMY_DAMAGE", "ALLY_BOOST", "SELF_BUFF", "CHARGE_BUFF", "READY_SELF", "DRAW", "HUNTER_MARK");
    /** Acciones que actuan sobre los objetivos elegidos: solo funcionan con ON_PLACED (los demas disparadores no reciben objetivos). */
    static final Set<String> TARGET_ACTIONS = Set.of("ENEMY_DAMAGE", "ALLY_BOOST", "HUNTER_MARK");
    static final Set<String> GROUPS = Set.of("ALLY", "ENEMY", "ANY_UNIT");

    private final CardTemplateRepository repository;

    public CatalogChecker(CardTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<CardTemplateEntity> cards = repository.findAll();
            int problems = 0;
            for (CardTemplateEntity c : cards) {
                for (String p : check(c)) { System.out.println("[CATALOGO] " + p); problems++; }
            }
            System.out.println("[CATALOGO] " + cards.size() + " cartas revisadas, " + problems + " aviso(s)."
                    + (cards.isEmpty() ? " (La tabla card_templates esta vacia: carga docs/supabase/cartas.sql.)" : ""));
        } catch (Exception e) {
            System.out.println("[CATALOGO] No se pudo revisar el catalogo: " + e.getClass().getSimpleName());
        }
    }

    /** Devuelve los problemas de una carta ("ERROR" = la carta falla al jugarla; "AVISO" = funciona pero no hace lo que parece). */
    public static List<String> check(CardTemplateEntity c) {
        List<String> out = new ArrayList<>();
        String who = (c.getId() == null ? "?" : c.getId()) + " '" + c.getName() + "'";
        if (c.getId() == null || c.getId().isBlank()) out.add("ERROR carta sin id");
        if (c.getName() == null || c.getName().isBlank()) out.add("ERROR " + who + ": falta el nombre");

        boolean unit = "UNIT".equals(c.getCardType());
        boolean spell = "SPELL".equals(c.getCardType());
        if (!unit && !spell) out.add("ERROR " + who + ": card_type debe ser UNIT o SPELL (esta '" + c.getCardType() + "')");
        try { Faction.valueOf(c.getFaction()); }
        catch (Exception e) { out.add("ERROR " + who + ": faction '" + c.getFaction() + "' no existe (usa " + Arrays.toString(Faction.values()) + ")"); }

        String speed = c.getSpellSpeed();
        if (unit && !"NOT_A_SPELL".equals(speed)) out.add("ERROR " + who + ": una UNIT debe tener spell_speed NOT_A_SPELL (tiene '" + speed + "')");
        if (spell && !Set.of("SLOW", "FAST", "INSTANT").contains(String.valueOf(speed))) out.add("ERROR " + who + ": un SPELL debe ser SLOW, FAST o INSTANT (tiene '" + speed + "')");
        if (c.getBaseCost() < 0 || c.getBaseCost() > 10) out.add("AVISO " + who + ": coste " + c.getBaseCost() + " (el recurso maximo es 10: no se podria jugar)");
        if (unit && c.getBaseDrive() <= 0) out.add("AVISO " + who + ": una unidad con Drive " + c.getBaseDrive() + " muere al instante");

        // ---- targets: "NONE" o "cantidad:GRUPO;cantidad:GRUPO"
        List<String> groups = new ArrayList<>();
        String targets = norm(c.getTargets());
        if (!targets.equals("NONE")) {
            for (String req : targets.split(";")) {
                String[] p = req.split(":");
                if (p.length != 2) { out.add("AVISO " + who + ": target '" + req + "' ignorado (formato cantidad:GRUPO, p. ej. 1:ENEMY)"); continue; }
                try {
                    int n = Integer.parseInt(p[0].trim());
                    String g = p[1].trim().toUpperCase();
                    if (n < 1) out.add("AVISO " + who + ": target '" + req + "' pide " + n + " objetivos");
                    if (!GROUPS.contains(g)) {
                        out.add("ERROR " + who + ": grupo de objetivo '" + g + "' no soportado (usa ALLY, ENEMY o ANY_UNIT)"
                                + ("GLOBAL".equals(g) || "LANE".equals(g) ? ": existe en el codigo pero el validador lo rechaza siempre" : ""));
                    } else groups.add(g);
                } catch (NumberFormatException e) {
                    out.add("AVISO " + who + ": target '" + req + "' ignorado: '" + p[0] + "' no es un numero");
                }
            }
        }

        // ---- keywords: "NONE" o "A,B" (separadas por COMA, en mayusculas)
        String kws = norm(c.getKeywords());
        if (!kws.equals("NONE")) {
            if (kws.contains(";")) out.add("ERROR " + who + ": las keywords se separan con COMA, no con ';' ('" + kws + "')");
            for (String k : kws.split("[,;]")) {
                try {
                    Keyword kw = Keyword.valueOf(k.trim());
                    if (kw == Keyword.GLORY || kw == Keyword.CHARGE) {
                        out.add("AVISO " + who + ": la keyword " + kw + " existe pero el motor no la lee; usa un efecto ("
                                + (kw == Keyword.GLORY ? "ON_COMBAT_WIN:READY_SELF" : "ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1") + ")");
                    }
                    if (spell) out.add("AVISO " + who + ": los hechizos no usan keywords ('" + k.trim() + "' no hara nada)");
                } catch (IllegalArgumentException e) {
                    out.add("ERROR " + who + ": keyword '" + k.trim() + "' desconocida: la partida falla al sacar esta carta (usa " + Arrays.toString(Keyword.values()) + ")");
                }
            }
        }

        // ---- effects: "NONE" o "DISPARADOR:ACCION:valor;..."
        String fx = norm(c.getEffects());
        if (!fx.equals("NONE")) {
            for (String rule : fx.split(";")) {
                String[] p = rule.split(":");
                if (p.length < 2) { out.add("AVISO " + who + ": efecto '" + rule + "' ignorado (formato DISPARADOR:ACCION:valor)"); continue; }
                String trigger = p[0].trim().toUpperCase(), action = p[1].trim().toUpperCase();
                if (!TRIGGERS.contains(trigger)) {
                    out.add("AVISO " + who + ": el disparador '" + trigger + "' NO existe: el efecto no se disparara nunca (validos: " + TRIGGERS + ")");
                }
                if (!ACTIONS.contains(action)) {
                    out.add("AVISO " + who + ": la accion '" + action + "' no existe: el efecto se ignora (validas: " + ACTIONS + ")");
                    continue;
                }
                int needed = "HUNTER_MARK".equals(action) ? 2 : ("READY_SELF".equals(action) ? 0 : 1);
                for (int i = 0; i < needed; i++) {
                    if (p.length <= 2 + i) { out.add("ERROR " + who + ": '" + rule + "' necesita " + needed + " valor(es) numerico(s) tras la accion: la partida falla al sacar esta carta"); break; }
                    try { if (Integer.parseInt(p[2 + i].trim()) < 1) out.add("AVISO " + who + ": '" + rule + "' con valor menor que 1"); }
                    catch (NumberFormatException e) { out.add("ERROR " + who + ": '" + rule + "': '" + p[2 + i] + "' no es un numero: la partida falla al sacar esta carta"); }
                }
                if (TARGET_ACTIONS.contains(action)) {
                    if (!"ON_PLACED".equals(trigger)) out.add("AVISO " + who + ": " + action + " solo funciona con ON_PLACED (los otros disparadores no reciben objetivos)");
                    else if (groups.isEmpty()) out.add("AVISO " + who + ": " + action + " necesita 'targets' (ahora NONE) o no afectara a nadie");
                    else if ("ENEMY_DAMAGE".equals(action) || "HUNTER_MARK".equals(action)) {
                        if (!groups.contains("ENEMY") && !groups.contains("ANY_UNIT")) out.add("AVISO " + who + ": " + action + " solo daña unidades ENEMY y los targets son " + groups);
                    } else if (!groups.contains("ALLY") && !groups.contains("ANY_UNIT")) out.add("AVISO " + who + ": ALLY_BOOST solo refuerza unidades ALLY y los targets son " + groups);
                }
                if (spell && !"ON_PLACED".equals(trigger)) out.add("AVISO " + who + ": un hechizo solo ejecuta ON_PLACED (al resolverse); '" + trigger + "' no se disparara");
                if (!groups.isEmpty() && !TARGET_ACTIONS.contains(action) && "ON_PLACED".equals(trigger) && !"NONE".equals(targets) && fx.split(";").length == 1) {
                    out.add("AVISO " + who + ": tiene targets pero " + action + " no usa objetivos: el jugador los elegira sin efecto");
                }
            }
        }
        return out;
    }

    private static String norm(String s) { return s == null || s.isBlank() ? "NONE" : s.trim(); }
}
