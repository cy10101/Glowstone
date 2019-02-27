package net.glowstone.command.glowstone;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.glowstone.GlowWorld;
import net.glowstone.ServerProvider;
import net.glowstone.command.CommandUtils;
import net.glowstone.command.minecraft.GlowVanillaCommand;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.i18n.LocalizedStringImpl;
import net.glowstone.util.ReflectionProcessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NonNls;

public class GlowstoneCommand extends GlowVanillaCommand {

    /**
     * Each value's name is the actual subcommand name (case-insensitive with English case folding,
     * displayed in lowercase).
     */
    private enum Subcommand {
        ABOUT {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                ResourceBundle b = commandMessages.getResourceBundle();
                // some info about this Glowstone server
                new LocalizedStringImpl("glowstone.about", b).send(sender);
                LocalizedStringImpl t
                        = new LocalizedStringImpl("glowstone.about._template", b);
                sendBullet(sender, t, b, "glowstone.about.brand", Bukkit.getName());
                sendBullet(sender, t, b, "glowstone.about.name", Bukkit.getServerName());
                sendBullet(sender, t, b, "glowstone.about.version", Bukkit.getVersion());
                sendBullet(sender, t, b, "glowstone.about.api-version", Bukkit.getBukkitVersion());
                sendBullet(sender, t, b, "glowstone.about.players",
                        Bukkit.getOnlinePlayers().size());
                sendBullet(sender, t, b, "glowstone.about.worlds", Bukkit.getWorlds().size());
                sendBullet(sender, t, b, "glowstone.about.plugins",
                        Bukkit.getPluginManager().getPlugins().length);

                // thread count
                int threadCount = 0;
                Set<Thread> threads = Thread.getAllStackTraces().keySet();
                for (Thread thread : threads) {
                    if (thread.getThreadGroup() == Thread.currentThread().getThreadGroup()) {
                        threadCount++;
                    }
                }
                sendBullet(sender, t, b, "glowstone.about.threads", threadCount);
                return false;
            }
        }, CHUNK {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                if (!CommandUtils.isPhysical(sender)) {
                    commandMessages.getNotPhysical().sendInColor(ChatColor.RED, sender);
                    return false;
                }
                Chunk chunk = CommandUtils.getLocation(sender).getChunk();
                new LocalizedStringImpl("glowstone.chunk", commandMessages.getResourceBundle())
                        .send(sender, chunk.getX(), chunk.getZ());
                return true;
            }
        }, EVAL {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                if (args.length == 1) {
                    sendHelp(sender, label, commandMessages.getResourceBundle());
                    return false;
                }
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    builder.append(args[i] + (i == args.length - 1 ? "" : " "));
                }
                ReflectionProcessor processor = new ReflectionProcessor(builder.toString(),
                        sender instanceof Entity ? sender : ServerProvider.getServer());
                Object result = processor.process();
                if (result == null) {
                    new LocalizedStringImpl("glowstone.eval.null",
                            commandMessages.getResourceBundle())
                            .send(sender);
                } else {
                    new LocalizedStringImpl("glowstone.eval", commandMessages.getResourceBundle())
                            .send(sender, result);
                }
                return true;
            }
        }, HELP {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                for (Subcommand subcommand : Subcommand.values()) {
                    subcommand.sendHelp(sender, label, commandMessages.getResourceBundle());
                }
                return false;
            }
        }, PROPERTY {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                ResourceBundle bundle = commandMessages.getResourceBundle();
                if (args.length == 1) {
                    LocalizedStringImpl propertyTemplate = new LocalizedStringImpl(
                            "glowstone.property", bundle);
                    // list all
                    System.getProperties().forEach(
                        (key, value) -> propertyTemplate.send(sender, key, value));
                } else {
                    // get a property
                    String key = args[1].toLowerCase();
                    String value = System.getProperty(key);
                    if (value == null) {
                        new LocalizedStringImpl("glowstone.property.invalid", bundle)
                                .sendInColor(ChatColor.RED, sender, key);
                    } else {
                        new LocalizedStringImpl("glowstone.property", bundle)
                                .send(sender, key, value);
                    }
                }
                return false;
            }
        }, VM {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
                List<String> arguments = runtimeMxBean.getInputArguments();
                ResourceBundle bundle = commandMessages.getResourceBundle();
                if (arguments.size() == 0) {
                    new LocalizedStringImpl("glowstone.vm.empty", bundle).send(sender);
                } else {
                    new LocalizedStringImpl("glowstone.vm", bundle).send(sender, arguments.size());
                    for (String argument : arguments) {
                        sender.sendMessage(
                                " - '" + ChatColor.AQUA + argument + ChatColor.RESET + "'.");
                    }
                }
                return false;
            }
        }, WORLD {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                ResourceBundle bundle = commandMessages.getResourceBundle();
                if (args.length == 1) {
                    // list worlds
                    new LocalizedStringImpl("glowstone.worlds", bundle)
                            .send(sender, commandMessages.joinList(getWorldNames()));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    new LocalizedStringImpl("glowstone.world.not-player", bundle)
                            .sendInColor(ChatColor.RED, sender);
                    return false;
                }
                GlowPlayer player = (GlowPlayer) sender;
                String worldName = args[1];
                GlowWorld world = player.getServer().getWorld(worldName);
                if (world == null) {
                    new LocalizedStringImpl("glowstone.world.invalid", bundle)
                            .sendInColor(ChatColor.RED, sender, worldName);
                    return false;
                }
                player.teleport(world.getSpawnLocation());
                new LocalizedStringImpl("glowstone.world.done", bundle)
                        .send(player, world.getName());
                return true;
            }
        },
        /**
         * Alias for {@link #WORLD}.
         */
        WORLDS {
            @Override
            boolean execute(CommandSender sender, String label, String[] args,
                    CommandMessages commandMessages) {
                return WORLD.execute(sender, label, args, commandMessages);
            }
        };

        private final @NonNls String lowerCaseName = toString().toLowerCase(Locale.ENGLISH);
        private final @NonNls String keyPrefix
                = "glowstone.subcommand." + lowerCaseName;
        private final @NonNls String usageKey = keyPrefix + ".usage";
        private final @NonNls String descriptionKey = keyPrefix + ".description";

        void sendHelp(CommandSender sender, String label, ResourceBundle resourceBundle) {
            sender.sendMessage("- " + ChatColor.GOLD + "/" + label + " "
                    + ChatColor.AQUA + new LocalizedStringImpl(usageKey, resourceBundle).get()
                    + ChatColor.GRAY + ": "
                    + new LocalizedStringImpl(descriptionKey, resourceBundle).get());
        }

        abstract boolean execute(CommandSender sender, String label, String[] args,
                CommandMessages commandMessages);
    }

    private static final @NonNls List<String> SUBCOMMANDS = Arrays.stream(Subcommand.values())
            .map(subcommand -> subcommand.lowerCaseName)
            .collect(ImmutableList.toImmutableList());
    private static final ImmutableSortedMap<String, Subcommand> SUBCOMMAND_MAP;

    static {
        Collator englishCaseInsensitive = Collator.getInstance(Locale.ENGLISH);
        englishCaseInsensitive.setStrength(Collator.PRIMARY);
        SUBCOMMAND_MAP = Arrays.stream(Subcommand.values())
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        englishCaseInsensitive, Object::toString,
                        Function.identity()));
    }

    /**
     * Creates the instance for this command.
     */
    public GlowstoneCommand() {
        super("glowstone", Arrays.asList("gs")); // NON-NLS
        setPermission("glowstone.debug"); // NON-NLS
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args,
            CommandMessages commandMessages) {
        if (!testPermission(sender, commandMessages.getPermissionMessage())) {
            return true;
        }
        Subcommand subcommand = null;
        if (args.length >= 1) {
            subcommand = SUBCOMMAND_MAP.get(args[0]);
        }
        if (subcommand == null) {
            sendUsageMessage(sender, commandMessages);
            return false;
        }
        return subcommand.execute(sender, label, args, commandMessages);
    }

    private static void sendBullet(CommandSender sender,
            LocalizedStringImpl template, ResourceBundle resourceBundle, @NonNls String key,
            Object value) {
        template.send(sender, new LocalizedStringImpl(key, resourceBundle), value);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args)
            throws IllegalArgumentException {
        Preconditions.checkNotNull(sender, "Sender cannot be null"); // NON-NLS
        Preconditions.checkNotNull(args, "Arguments cannot be null"); // NON-NLS
        Preconditions.checkNotNull(alias, "Alias cannot be null"); // NON-NLS
        switch (args.length) {
            case 1:
                return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS,
                        new ArrayList<>(SUBCOMMANDS.size()));
            case 2:
                switch (SUBCOMMAND_MAP.get(args[0])) {
                    case PROPERTY:
                        return StringUtil
                                .copyPartialMatches(args[1],
                                        System.getProperties().stringPropertyNames(),
                                        new ArrayList<>(
                                                System.getProperties().stringPropertyNames()
                                                        .size()));
                    case WORLD:
                        if (sender instanceof Player) {
                            Collection<String> worlds = getWorldNames();
                            return StringUtil
                                    .copyPartialMatches(args[1], worlds,
                                            new ArrayList<>(worlds.size()));
                        } // else fall through
                    default:
                    // fall through
                }
                // fall through
            default:
            // fall through
        }
        return Collections.emptyList();
    }

    private static Collection<String> getWorldNames() {
        return ServerProvider.getServer().getWorlds().stream().map(World::getName)
                .collect(Collectors.toList());
    }
}
