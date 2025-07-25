package com.volmit.iris.core.nms.v1_20_R4;

import com.mojang.serialization.MapCodec;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.reflect.NMSRef;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R4.CraftServer;
import org.bukkit.craftbukkit.v1_20_R4.CraftWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomBiomeSource extends BiomeSource {

    private final long seed;
    private final Engine engine;
    private final Registry<Biome> biomeCustomRegistry;
    private final Registry<Biome> biomeRegistry;
    private final AtomicCache<RegistryAccess> registryAccess = new AtomicCache<>();
    private final RNG rng;
    private final KMap<String, Holder<Biome>> customBiomes;

    public CustomBiomeSource(long seed, Engine engine, World world) {
        this.engine = engine;
        this.seed = seed;
        this.biomeCustomRegistry = registry().registry(Registries.BIOME).orElse(null);
        this.biomeRegistry = ((RegistryAccess) NMSRef.getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer())).registry(Registries.BIOME).orElse(null);
        this.rng = new RNG(engine.getSeedManager().getBiome());
        this.customBiomes = fillCustomBiomes(biomeCustomRegistry, engine);
    }

    private static List<Holder<Biome>> getAllBiomes(Registry<Biome> customRegistry, Registry<Biome> registry, Engine engine) {
        List<Holder<Biome>> b = new ArrayList<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    b.add(customRegistry.getHolder(customRegistry.getResourceKey(customRegistry
                            .get(new ResourceLocation(engine.getDimension().getLoadKey() + ":" + j.getId()))).get()).get());
                }
            } else {
                b.add(NMSBinding.biomeToBiomeBase(registry, i.getVanillaDerivative()));
            }
        }

        return b;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return getAllBiomes(
                ((RegistryAccess) NMSRef.getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()))
                        .registry(Registries.BIOME).orElse(null),
                ((CraftWorld) engine.getWorld().realWorld()).getHandle().registryAccess().registry(Registries.BIOME).orElse(null),
                engine).stream();
    }
    private KMap<String, Holder<Biome>> fillCustomBiomes(Registry<Biome> customRegistry, Engine engine) {
        KMap<String, Holder<Biome>> m = new KMap<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    ResourceLocation resourceLocation = new ResourceLocation(engine.getDimension().getLoadKey() + ":" + j.getId());
                    Biome biome = customRegistry.get(resourceLocation);
                    Optional<ResourceKey<Biome>> optionalBiomeKey = customRegistry.getResourceKey(biome);
                    if (optionalBiomeKey.isEmpty()) {
                        Iris.error("Cannot find biome for IrisBiomeCustom " + j.getId() + " from engine " + engine.getName());
                        continue;
                    }
                    ResourceKey<Biome> biomeKey = optionalBiomeKey.get();
                    Optional<Holder.Reference<Biome>> optionalReferenceHolder = customRegistry.getHolder(biomeKey);
                    if (optionalReferenceHolder.isEmpty()) {
                        Iris.error("Cannot find reference to biome " + biomeKey + " for engine " + engine.getName());
                        continue;
                    }
                    m.put(j.getId(), optionalReferenceHolder.get());
                }
            }
        }

        return m;
    }

    private RegistryAccess registry() {
        return registryAccess.aquire(() -> (RegistryAccess) NMSRef.getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int m = (y - engine.getMinHeight()) << 2;
        IrisBiome ib = engine.getComplex().getTrueBiomeStream().get(x << 2, z << 2);
        if (ib.isCustom()) {
            return customBiomes.get(ib.getCustomBiome(rng, x << 2, m, z << 2).getId());
        } else {
            org.bukkit.block.Biome v = ib.getSkyBiome(rng, x << 2, m, z << 2);
            return NMSBinding.biomeToBiomeBase(biomeRegistry, v);
        }
    }
}