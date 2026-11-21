package com.kryos.mascotas;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Datos de mascotas de un jugador. */
public class PlayerPets {

    public final Set<String> unlocked = new LinkedHashSet<>();
    public final Map<String, Progress> progress = new HashMap<>();
    public String active;

    public Progress get(String id) {
        return progress.computeIfAbsent(id, k -> new Progress());
    }

    public static class Progress {
        public int level = 1;
        public double xp = 0;
        /** "auto" = mejor estela desbloqueada, "none" = sin estela, o el nombre de una partícula. */
        public String particle = "auto";
        /** Niveles cuyas recompensas ya se entregaron. */
        public final Set<Integer> claimed = new HashSet<>();
        /** Enemigos derrotados con esta mascota activa. */
        public int kills = 0;
        /** Apodo puesto por el jugador (null = nombre por defecto). */
        public String nickname;
    }
}
