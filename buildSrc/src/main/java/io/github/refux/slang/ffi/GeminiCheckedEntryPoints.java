package io.github.refux.slang.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class GeminiCheckedEntryPoints {
    private GeminiCheckedEntryPoints() {
    }

    public static IEntryPoint find(IModule module, String name, int stage) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment outDiagnostics = arena.allocate(ValueLayout.ADDRESS);
            int result = io.github.refux.slang.ffi.gen.IModule.findAndCheckEntryPoint(
                    module.segment(), arena.allocateFrom(name), stage,
                    outEntryPoint, outDiagnostics);
            Diagnostics.check("IModule::findAndCheckEntryPoint", result, outDiagnostics);
            return new IEntryPoint(outEntryPoint.get(ValueLayout.ADDRESS, 0));
        }
    }
}
