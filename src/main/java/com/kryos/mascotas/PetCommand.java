package com.kryos.mascotas;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PetCommand implements CommandExecutor, TabCompleter {

    private final KryosMascotas plugin;
    private final PetManager pm;

    public PetCommand(KryosMascotas plugin, PetManager pm) {
        this.plugin = plugin;
        this.pm = pm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                plugin.msg(sender, "<red>Este comando es solo para jugadores.");
                return true;
            }
            new PetsMenu(plugin, pm, p, 0).open();
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invocar" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length < 2) {
                    plugin.msg(p, "<gray>Uso: <white>/mascota invocar <id>");
                } else if (!pm.spawn(p, args[1].toLowerCase(Locale.ROOT), false)) {
                    plugin.msg(p, "<red>Esa mascota no existe o aún no la has desbloqueado.");
                }
            }
            case "ver", "niveles" -> {
                if (!(sender instanceof Player p)) return true;
                PlayerPets pp = pm.data(p.getUniqueId());
                String id = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : pp.active;
                if (id == null || pm.petSec(id) == null || !pp.unlocked.contains(id)) {
                    plugin.msg(p, "<red>Indica una mascota desbloqueada o invoca una primero.");
                    return true;
                }
                if (args[0].equalsIgnoreCase("niveles")) new LevelsMenu(plugin, pm, p, id, -1).open();
                else new PetMenu(plugin, pm, p, id).open();
            }
            case "guardar" -> {
                if (!(sender instanceof Player p)) return true;
                pm.dismiss(p);
            }
            case "admin" -> admin(sender, args);
            default -> plugin.msg(sender, "<gray>Uso: <white>/mascota [invocar <id> | ver [id] | niveles [id] | guardar]");
        }
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kryosmascotas.admin")) {
            plugin.msg(sender, "<red>No tienes permiso.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.msg(sender, "<green>Configuración recargada.");
            return;
        }
        if (args.length < 4) {
            plugin.msg(sender, "<gray>Uso: <white>/mascota admin <dar|nivel|xp> <jugador> <mascota> [valor]");
            plugin.msg(sender, "<gray>También: <white>/mascota admin reload");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        String petId = args[3].toLowerCase(Locale.ROOT);
        if (target == null) {
            plugin.msg(sender, "<red>Jugador no conectado.");
            return;
        }
        if (pm.petSec(petId) == null) {
            plugin.msg(sender, "<red>Esa mascota no existe en la config.");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "dar" -> {
                pm.unlock(target, petId);
                plugin.msg(sender, "<green>Mascota desbloqueada para " + target.getName() + ".");
                plugin.msg(target, "<green>¡Has recibido una nueva mascota!");
            }
            case "nivel" -> {
                if (args.length < 5) {
                    plugin.msg(sender, "<gray>Falta el nivel.");
                    return;
                }
                try {
                    pm.setLevel(target, petId, Integer.parseInt(args[4]));
                    plugin.msg(sender, "<green>Nivel actualizado.");
                } catch (NumberFormatException e) {
                    plugin.msg(sender, "<red>Número inválido.");
                }
            }
            case "xp" -> {
                if (args.length < 5) {
                    plugin.msg(sender, "<gray>Falta la cantidad.");
                    return;
                }
                try {
                    pm.addXp(target, petId, Double.parseDouble(args[4]));
                    plugin.msg(sender, "<green>XP añadida.");
                } catch (NumberFormatException e) {
                    plugin.msg(sender, "<red>Número inválido.");
                }
            }
            default -> plugin.msg(sender, "<red>Subcomando desconocido.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("invocar");
            out.add("ver");
            out.add("niveles");
            out.add("guardar");
            if (sender.hasPermission("kryosmascotas.admin")) out.add("admin");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("invocar") || args[0].equalsIgnoreCase("ver") || args[0].equalsIgnoreCase("niveles")) && sender instanceof Player p) {
            out.addAll(pm.data(p.getUniqueId()).unlocked);
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("kryosmascotas.admin")) {
            if (args.length == 2) out.addAll(List.of("dar", "nivel", "xp", "reload"));
            else if (args.length == 3) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            else if (args.length == 4) out.addAll(pm.petIds());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
