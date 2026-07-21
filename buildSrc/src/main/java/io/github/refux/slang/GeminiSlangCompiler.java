package io.github.refux.slang;

import io.github.refux.slang.ffi.GeminiCheckedEntryPoints;
import io.github.refux.slang.ffi.IGlobalSession;
import io.github.refux.slang.ffi.IModule;
import io.github.refux.slang.ffi.PreprocessorMacroDesc;
import io.github.refux.slang.ffi.SessionDesc;
import io.github.refux.slang.ffi.SlangGlobalSessionDesc;
import io.github.refux.slang.ffi.SlangNative;
import io.github.refux.slang.ffi.TargetDesc;
import io.github.refux.slang.ffi.gen.CompilerOptionEntry;
import io.github.refux.slang.ffi.gen.CompilerOptionName;
import io.github.refux.slang.ffi.gen.CompilerOptionValue;
import io.github.refux.slang.ffi.gen.CompilerOptionValueKind;
import io.github.refux.slang.ffi.gen.SlangSourceLanguage;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public final class GeminiSlangCompiler implements AutoCloseable {
    private final IGlobalSession ffi;
    private final GlobalSession global;

    public GeminiSlangCompiler() {
        ffi = createGlobalSession();
        global = new GlobalSession(ffi);
    }

    public String buildTag() {
        return global.buildTagString();
    }

    public byte[] compileSlang(String moduleName, String source, String profile,
                               String define, List<String> diagnostics) {
        try (Session session = createSession(CompileTarget.GLSL, profile, null, define, diagnostics)) {
            Module module = session.loadModuleFromSource(moduleName, source);
            try (ComponentType linked = session.composite(module, module.entryPoint("main")).link()) {
                return linked.entryPointCode(0, 0);
            }
        }
    }

    public byte[] validateGlsl(String moduleName, String source, Stage stage,
                               List<String> diagnostics) {
        try (Session session = createSession(CompileTarget.SPIRV, "glsl_450", stage,
                stage == Stage.VERTEX ? "gl_VertexID=gl_VertexIndex" : null, diagnostics)) {
            Module module = session.loadModuleFromSource(moduleName, source);
            EntryPoint entryPoint = checkedEntryPoint(module, session, "main", stage);
            try (ComponentType linked = session.composite(module, entryPoint).link()) {
                return linked.entryPointCode(0, 0);
            }
        }
    }

    private static EntryPoint checkedEntryPoint(Module module, Session session,
                                                 String name, Stage stage) {
        IModule handle = (IModule) module.componentHandle();
        return new EntryPoint(session, GeminiCheckedEntryPoints.find(handle, name, stage.value()));
    }

    private Session createSession(CompileTarget target, String profile, Stage sourceStage,
                                  String define, List<String> diagnostics) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment targetArray = TargetDesc.allocateArray(arena, 1);
            TargetDesc.setFormat(targetArray, target.value());
            int profileId = ffi.findProfile(profile);
            if (profileId == 0) {
                throw new IllegalArgumentException("Unknown Slang profile: " + profile);
            }
            TargetDesc.setProfile(targetArray, profileId);

            List<Option> options = new ArrayList<>();
            options.add(Option.integer(CompilerOptionName.AllowGLSL, 1));
            options.add(Option.integer(CompilerOptionName.NoMangle, 1));
            options.add(Option.integer(CompilerOptionName.PreserveParameters, 1));
            options.add(Option.string(CompilerOptionName.DisableWarnings, "41012,41024"));
            if (sourceStage != null) {
                options.add(Option.integer(CompilerOptionName.Language,
                        SlangSourceLanguage.SLANG_SOURCE_LANGUAGE_GLSL));
                options.add(Option.integer(CompilerOptionName.Stage, sourceStage.value()));
            }
            MemorySegment optionArray = CompilerOptionEntry.allocateArray(arena, options.size());
            for (int i = 0; i < options.size(); i++) {
                Option option = options.get(i);
                MemorySegment entry = CompilerOptionEntry.element(optionArray, i);
                CompilerOptionEntry.setName(entry, option.name());
                MemorySegment value = CompilerOptionEntry.value(entry);
                if (option.stringValue() != null) {
                    CompilerOptionValue.setKind(value, CompilerOptionValueKind.String);
                    CompilerOptionValue.setStringValue0(value, arena.allocateFrom(option.stringValue()));
                } else {
                    CompilerOptionValue.setKind(value, CompilerOptionValueKind.Int);
                    CompilerOptionValue.setIntValue0(value, option.intValue());
                }
            }

            MemorySegment desc = SessionDesc.allocate(arena);
            SessionDesc.setTargets(desc, targetArray, 1);
            io.github.refux.slang.ffi.gen.SessionDesc.setCompilerOptionEntries(desc, optionArray);
            io.github.refux.slang.ffi.gen.SessionDesc.setCompilerOptionEntryCount(desc, options.size());
            if (define != null) {
                String[] parts = define.split("=", 2);
                MemorySegment macros = PreprocessorMacroDesc.allocateArray(arena, 1);
                PreprocessorMacroDesc.set(macros, 0, arena.allocateFrom(parts[0]),
                        arena.allocateFrom(parts.length == 2 ? parts[1] : "1"));
                SessionDesc.setPreprocessorMacros(desc, macros, 1);
            }
            return new Session(global, ffi.createSession(desc), diagnostics::add);
        }
    }

    private static IGlobalSession createGlobalSession() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment desc = SlangGlobalSessionDesc.allocate(arena);
            SlangGlobalSessionDesc.setEnableGlsl(desc, true);
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            int result = SlangNative.slang_createGlobalSession2(desc, out);
            if (!SlangNative.succeeded(result)) {
                throw new SlangException("slang_createGlobalSession2 failed", result);
            }
            return new IGlobalSession(out.get(ValueLayout.ADDRESS, 0));
        }
    }

    @Override
    public void close() {
        global.close();
    }

    private record Option(int name, int intValue, String stringValue) {
        static Option integer(int name, int value) {
            return new Option(name, value, null);
        }

        static Option string(int name, String value) {
            return new Option(name, 0, value);
        }
    }
}
