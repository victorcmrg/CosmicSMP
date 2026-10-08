package com.cosmicsmp.feature.ability.impl.cosmic;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.task.TimedEffect;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.Targeting;
import com.cosmicsmp.feature.ability.TimedStateService;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cosmic Primary #2: become a mirror of another player for 4 minutes - their skin, name (tag, tab, chat) and
 * armour with trims. The armour copy is client-side only (fake equipment packets), so nothing can be duplicated.
 * The disguise is stored as a timed state and resumed after a relog / restart for the remaining time.
 */
public final class MirrorClone implements Ability {

    public static final String STATE = "mirror_clone";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final CosmicSMP plugin;

    public MirrorClone(CosmicSMP plugin) {
        this.plugin = plugin;
        plugin.timedStates().register(STATE, new TimedStateService.Handler() {
            @Override
            public void resume(Player player, PlayerData.TimedState state, long remainingMillis) {
                Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
                for (EquipmentSlot slot : ARMOR) {
                    String raw = state.get("armor." + slot.name());
                    if (raw != null && !raw.isEmpty()) {
                        try {
                            armor.put(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(raw)));
                        } catch (RuntimeException ignored) {
                            // unreadable item, skip
                        }
                    }
                }
                plugin.effects().start(new Disguise(plugin, player, state.get("name"), state.get("texture"), state.get("signature"),
                        armor, (int) (remainingMillis / 50L), false));
            }

            @Override
            public void expire(Player player, PlayerData.TimedState state) {
                // the real profile is restored by the login itself; nothing to undo
            }
        });
    }

    @Override
    public String id() {
        return "mirror_clone";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity hit = Targeting.entityInSight(ctx.plugin(), ctx.caster(), ctx.def().num("range", 30), 0.6, true);
        if (!(hit instanceof Player target)) {
            return CastResult.NO_TARGET;
        }
        Player caster = ctx.caster();
        plugin.effects().find(caster.getUniqueId(), Disguise.class).ifPresent(d -> d.cancel(EndReason.CANCELLED));

        String texture = null;
        String signature = null;
        for (ProfileProperty property : target.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                texture = property.getValue();
                signature = property.getSignature();
            }
        }
        Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
        if (ctx.def().bool("copy-armor", true)) {
            for (EquipmentSlot slot : ARMOR) {
                ItemStack item = target.getInventory().getItem(slot);
                if (item != null && !item.isEmpty()) {
                    armor.put(slot, item.clone());
                }
            }
        }
        int ticks = ctx.def().integer("duration-seconds", 240) * 20;
        PlayerData.TimedState state = plugin.timedStates().set(caster, STATE, ticks * 50L);
        state.put("name", target.getName());
        state.put("texture", texture == null ? "" : texture);
        state.put("signature", signature == null ? "" : signature);
        armor.forEach((slot, item) -> state.put("armor." + slot.name(), Base64.getEncoder().encodeToString(item.serializeAsBytes())));
        plugin.effects().start(new Disguise(plugin, caster, target.getName(), texture, signature, armor, ticks, true));
        plugin.messages().send(caster, "abilities.mirror-clone", Placeholders.of("target", target.getName(),
                "time", ctx.def().integer("duration-seconds", 240) / 60 + "m"));
        return CastResult.SUCCESS;
    }

    static final class Disguise extends TimedEffect {
        private final Player player;
        private final String name;
        private final String texture;
        private final String signature;
        private final Map<EquipmentSlot, ItemStack> armor;
        private final boolean announce;
        private PlayerProfile original;

        Disguise(CosmicSMP plugin, Player player, String name, String texture, String signature,
                 Map<EquipmentSlot, ItemStack> armor, int ticks, boolean announce) {
            super(Math.max(20, ticks));
            this.player = player;
            this.name = name;
            this.texture = texture == null || texture.isEmpty() ? null : texture;
            this.signature = signature == null || signature.isEmpty() ? null : signature;
            this.armor = armor;
            this.announce = announce;
        }

        @Override
        public UUID owner() {
            return player.getUniqueId();
        }

        @Override
        protected void onStart() {
            PlayerProfile current = player.getPlayerProfile();
            original = Bukkit.createProfile(current.getId(), current.getName());
            original.setProperties(current.getProperties());

            PlayerProfile disguise = Bukkit.createProfile(player.getUniqueId(), name);
            if (texture != null) {
                disguise.setProperty(new ProfileProperty("textures", texture, signature));
            }
            player.setPlayerProfile(disguise);
            player.displayName(Component.text(name));
            player.playerListName(Component.text(name));
            Location at = player.getLocation().add(0, 1, 0);
            ParticleSpec.of(Particle.WITCH, 30, 0.4, 0.1).spawn(at, com.cosmicsmp.core.fx.Fx.viewers(at));
            if (announce) {
                SoundSpec.of("entity.illusioner.mirror_move", 1.2f, 1.0f).play(at);
            }
            Bukkit.getScheduler().runTaskLater(plugin, this::sendFakeArmor, 5L);
        }

        @Override
        protected void onTick() {
            if (!player.isOnline()) {
                cancel(EndReason.QUIT);
                return;
            }
            if (age % 20 == 0) {
                sendFakeArmor();
            }
        }

        private void sendFakeArmor() {
            if (armor.isEmpty() || !player.isOnline()) {
                return;
            }
            Map<EquipmentSlot, ItemStack> view = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : ARMOR) {
                view.put(slot, armor.getOrDefault(slot, null));
            }
            for (Player viewer : player.getTrackedBy()) {
                viewer.sendEquipmentChange(player, view);
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            if (!player.isOnline()) {
                return;
            }
            if (original != null) {
                player.setPlayerProfile(original);
            }
            player.displayName(null);
            player.playerListName(null);
            Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : ARMOR) {
                real.put(slot, player.getInventory().getItem(slot));
            }
            for (Player viewer : player.getTrackedBy()) {
                viewer.sendEquipmentChange(player, real);
            }
            if (reason != EndReason.QUIT && reason != EndReason.SHUTDOWN) {
                plugin.timedStates().clear(player, STATE);
                Location at = player.getLocation().add(0, 1, 0);
                ParticleSpec.of(Particle.WITCH, 30, 0.4, 0.1).spawn(at, com.cosmicsmp.core.fx.Fx.viewers(at));
                SoundSpec.of("entity.illusioner.prepare_mirror", 1.0f, 1.2f).play(at);
                plugin.messages().send(player, "abilities.mirror-clone-end", Placeholders.empty());
            }
        }
    }
}
