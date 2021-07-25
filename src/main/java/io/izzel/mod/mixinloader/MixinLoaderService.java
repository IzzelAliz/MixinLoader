package io.izzel.mod.mixinloader;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.INameMappingService;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import joptsimple.OptionSpecBuilder;
import sun.misc.Unsafe;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class MixinLoaderService implements ITransformationService {

    private static final PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

    static MethodHandles.Lookup LOOKUP;
    static Unsafe UNSAFE;
    private static ILaunchPluginService plugin;

    static {
        out.println("MixinLoader 1.2 by IzzelAliz");
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
            Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object lookupBase = UNSAFE.staticFieldBase(lookupField);
            long lookupOffset = UNSAFE.staticFieldOffset(lookupField);
            LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(lookupBase, lookupOffset);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        try {
            addClassesToClassloader();
            out.println("Successfully add Mixin to classpath!");
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            Launcher instance = Launcher.INSTANCE;
            Field launchPlugins = Launcher.class.getDeclaredField("launchPlugins");
            launchPlugins.setAccessible(true);
            LaunchPluginHandler handler = (LaunchPluginHandler) launchPlugins.get(instance);
            Field plugins = LaunchPluginHandler.class.getDeclaredField("plugins");
            plugins.setAccessible(true);
            Map<String, ILaunchPluginService> map = (Map<String, ILaunchPluginService>) plugins.get(handler);
            plugin = (ILaunchPluginService) Class.forName("org.spongepowered.asm.launch.MixinLaunchPlugin").getConstructor().newInstance();
            map.put("mixin", new MixinLoaderLaunchPlugin(plugin));
            out.println("Successfully inject MixinLaunchPlugin!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MixinLoaderService() {
        try {
            delegate = Class.forName("org.spongepowered.asm.launch.MixinTransformationService").getConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // prevent classloading
    private Object delegate;

    @Override
    public String name() {
        return "mixin";
    }

    @Override
    public void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder) {
        try {
            Method arguments = delegate.getClass().getDeclaredMethod("arguments", BiFunction.class);
            arguments.invoke(delegate, argumentBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void argumentValues(OptionResult option) {
        try {
            Method arguments = delegate.getClass().getDeclaredMethod("argumentValues", OptionResult.class);
            arguments.invoke(delegate, option);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(IEnvironment environment) {
        try {
            Method initialize = delegate.getClass().getDeclaredMethod("initialize", IEnvironment.class);
            initialize.invoke(delegate, new IEnvironment() {
                @Override
                public <T> Optional<T> getProperty(TypesafeMap.Key<T> key) {
                    return environment.getProperty(key);
                }

                @Override
                public <T> T computePropertyIfAbsent(TypesafeMap.Key<T> key, Function<? super TypesafeMap.Key<T>, ? extends T> valueFunction) {
                    return environment.computePropertyIfAbsent(key, valueFunction);
                }

                @Override
                public Optional<ILaunchPluginService> findLaunchPlugin(String name) {
                    if (name.equals("mixin")) {
                        return Optional.of(plugin);
                    }
                    return environment.findLaunchPlugin(name);
                }

                @Override
                public Optional<ILaunchHandlerService> findLaunchHandler(String name) {
                    return environment.findLaunchHandler(name);
                }

                @Override
                public Optional<IModuleLayerManager> findModuleLayerManager() {
                    return environment.findModuleLayerManager();
                }

                @Override
                public Optional<BiFunction<INameMappingService.Domain, String, String>> findNameMapping(String targetMapping) {
                    return environment.findNameMapping(targetMapping);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public List<ITransformer> transformers() {
        return new ArrayList<>();
    }

    private static Path extract(String name, String target) throws Throwable {
        Path path = Paths.get(target);
        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            InputStream stream = MixinLoaderService.class.getResourceAsStream("/" + name);
            if (stream != null) {
                OutputStream outputStream = Files.newOutputStream(Paths.get(target));
                copy(stream, outputStream);
                stream.close();
                outputStream.close();
            }
        }
        return path;
    }

    private static void copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[4096];
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
        }
    }

    private static void addClassesToClassloader() throws Throwable {
        load(new Path[]{
            extract("mixin-0.8.3.jar", "./libraries/org/spongepowered/mixin/0.8.3/mixin-0.8.3.jar")
        });
    }

    private static void load(Path[] file) throws Throwable {
        var classLoader = (ModuleClassLoader) MixinLoaderService.class.getClassLoader();
        var fallbackField = ModuleClassLoader.class.getDeclaredField("fallbackClassLoader");
        var fallback = UNSAFE.getObject(classLoader, UNSAFE.objectFieldOffset(fallbackField));
        var secureJar = SecureJar.from(file);
        var configurationField = ModuleClassLoader.class.getDeclaredField("configuration");
        var confOffset = UNSAFE.objectFieldOffset(configurationField);
        var oldConf = (Configuration) UNSAFE.getObject(fallback, confOffset);
        var conf = oldConf.resolveAndBind(JarModuleFinder.of(secureJar), ModuleFinder.of(), List.of(secureJar.name()));
        UNSAFE.putObjectVolatile(fallback, confOffset, conf);
        var pkgField = ModuleClassLoader.class.getDeclaredField("packageLookup");
        var packageLookup = (Map<String, ResolvedModule>) UNSAFE.getObject(fallback, UNSAFE.objectFieldOffset(pkgField));
        var rootField = ModuleClassLoader.class.getDeclaredField("resolvedRoots");
        var resolvedRoots = (Map<String, Object>) UNSAFE.getObject(fallback, UNSAFE.objectFieldOffset(rootField));
        var moduleRefCtor = LOOKUP.findConstructor(Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference"),
            MethodType.methodType(void.class, Jar.class));
        for (var mod : conf.modules()) {
            for (var pk : mod.reference().descriptor().packages()) {
                packageLookup.put(pk, mod);
            }
            resolvedRoots.put(mod.name(), moduleRefCtor.invokeWithArguments(secureJar));
        }
    }
}
