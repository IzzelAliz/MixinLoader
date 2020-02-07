package io.izzel.mod.mixinloader;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import joptsimple.OptionSpecBuilder;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@SuppressWarnings("unchecked")
public class MixinLoaderService implements ITransformationService {

    private static PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

    private static MethodHandles.Lookup lookup;
    private static Unsafe UNSAFE;

    static {
        out.println("MixinLoader 1.1 by IzzelAliz");
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
            Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object lookupBase = UNSAFE.staticFieldBase(lookupField);
            long lookupOffset = UNSAFE.staticFieldOffset(lookupField);
            lookup = (MethodHandles.Lookup) UNSAFE.getObject(lookupBase, lookupOffset);
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
            map.put("mixin", (ILaunchPluginService) Class.forName("org.spongepowered.asm.launch.MixinLaunchPlugin").newInstance());
            out.println("Successfully inject MixinLaunchPlugin!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MixinLoaderService() {
        try {
            delegate = Class.forName("org.spongepowered.asm.launch.MixinTransformationService").newInstance();
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
            initialize.invoke(delegate, environment);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void beginScanning(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public List<ITransformer> transformers() {
        return new ArrayList<>();
    }

    private static void extract(String name, String target) throws Throwable {
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
        load(target, ITransformationService.class.getClassLoader());
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
        extract("mixin-0.8.jar", "./libraries/org/spongepowered/mixin/0.8/mixin-0.8.jar");
        extract("asm-util-7.2.jar", "./libraries/org/ow2/asm/asm-util/7.2/asm-util-7.2.jar");
        extract("asm-analysis-7.2.jar", "./libraries/org/ow2/asm/asm-analysis/7.2/asm-analysis-7.2.jar");
    }

    private static void load(String file, ClassLoader loader) throws Throwable {
            Field ucp = loader.getClass().getDeclaredField("ucp");
            long ucpOffset = UNSAFE.objectFieldOffset(ucp);
            Object urlClassPath = UNSAFE.getObject(loader, ucpOffset);
            MethodHandle methodHandle = lookup.findVirtual(urlClassPath.getClass(), "addURL", MethodType.methodType(void.class, java.net.URL.class));
            methodHandle.invoke(urlClassPath, Paths.get(file).toUri().toURL());
    }

}
