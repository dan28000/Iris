/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.mantle.ComponentFlag;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisFluidBodies;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;
import lombok.Getter;

@Getter
@ComponentFlag(MantleFlag.FLUID_BODIES)
public class MantleFluidBodyComponent extends IrisMantleComponent {
    private final int radius = computeRadius();

    public MantleFluidBodyComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.FLUID_BODIES, 0);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = new RNG(Cache.key(x, z) + seed() + 405666);
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        generate(writer, rng, x, z, region, biome);
    }

    @ChunkCoordinates
    private void generate(MantleWriter writer, RNG rng, int cx, int cz, IrisRegion region, IrisBiome biome) {
        generate(getDimension().getFluidBodies(), writer, new RNG((rng.nextLong() * cx) + 490495 + cz), cx, cz);
        generate(biome.getFluidBodies(), writer, new RNG((rng.nextLong() * cx) + 490495 + cz), cx, cz);
        generate(region.getFluidBodies(), writer, new RNG((rng.nextLong() * cx) + 490495 + cz), cx, cz);
    }

    @ChunkCoordinates
    private void generate(IrisFluidBodies bodies, MantleWriter writer, RNG rng, int cx, int cz) {
        bodies.generate(writer, rng, getEngineMantle().getEngine(), cx << 4, -1, cz << 4);
    }

    private int computeRadius() {
        int max = 0;

        max = Math.max(max, getDimension().getFluidBodies().getMaxRange(getData()));

        for (IrisRegion i : getDimension().getAllRegions(this::getData)) {
            max = Math.max(max, i.getFluidBodies().getMaxRange(getData()));
        }

        for (IrisBiome i : getDimension().getAllBiomes(this::getData)) {
            max = Math.max(max, i.getFluidBodies().getMaxRange(getData()));
        }

        return max;
    }
}
