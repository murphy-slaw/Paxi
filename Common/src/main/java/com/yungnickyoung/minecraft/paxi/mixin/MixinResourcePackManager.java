package com.yungnickyoung.minecraft.paxi.mixin;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.yungnickyoung.minecraft.paxi.PaxiFileResourcePackProvider;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Overwrites the vanilla method for building a list of enabled resource packs.
 * Should completely preserve vanilla behavior while adding Paxi packs separately in a specific order
 * as determined by the user's datapack_load_order.json.
 */
@Mixin(PackRepository.class)
public abstract class MixinResourcePackManager {
    @Shadow
    private Map<String, Pack> available;

    @Final
    @Shadow
    private Set<RepositorySource> sources;

    @Shadow
    private Stream<Pack> getAvailablePacks(Collection<String> names) {
        throw new AssertionError();
    }

    @Inject(at=@At("HEAD"), method="rebuildSelected", cancellable = true)
    private void buildEnabledProfiles(Collection<String> enabledNames, CallbackInfoReturnable<List<Pack>> cir) {
        // Fetch Paxi pack provider
        Optional<RepositorySource> paxiProvider = this.sources.stream().filter(provider -> provider instanceof PaxiFileResourcePackProvider).findFirst();

        // List of all packs to be marked as enabled
        List<Pack> allEnabledPacks = this.getAvailablePacks(enabledNames).collect(Collectors.toList());

        // List of all packs loaded by Paxi
        List<Pack> paxiPacks = new ArrayList<>();

        // Grab a list of all Paxi packs from the Paxi provider, if it exists.
        // We must gather Paxi packs separately because vanilla uses a TreeMap to store all packs, so they are
        // stored lexicographically, but for Paxi we need them to be enabled in a specific order
        // (determined by the user's datapack_load_order.json)
        if (paxiProvider.isPresent() && ((PaxiFileResourcePackProvider)paxiProvider.get()).orderedPaxiPacks.size() > 0) {
            paxiPacks = this.getAvailablePacks(((PaxiFileResourcePackProvider)paxiProvider.get()).orderedPaxiPacks).collect(Collectors.toList());
            allEnabledPacks.removeAll(paxiPacks);
        }

        // Register all Paxi packs
        for (Pack resourcePackProfile : paxiPacks) {
            if (resourcePackProfile.isRequired() && !allEnabledPacks.contains(resourcePackProfile)) {
                resourcePackProfile.getDefaultPosition().insert(allEnabledPacks, resourcePackProfile, Functions.identity(), false);
            }
        }

        // Register all other packs (lexicographical order)
        for (Pack resourcePackProfile : this.available.values()) {
            if (resourcePackProfile.isRequired() && !allEnabledPacks.contains(resourcePackProfile)) {
                resourcePackProfile.getDefaultPosition().insert(allEnabledPacks, resourcePackProfile, Functions.identity(), false);
            }
        }

        cir.setReturnValue(ImmutableList.copyOf(allEnabledPacks));
    }
}
