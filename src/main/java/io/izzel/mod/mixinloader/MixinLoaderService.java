package io.izzel.mod.mixinloader;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import joptsimple.OptionSpecBuilder;

import java.io.*;
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

    private static boolean addURLAvailable = true;
    private static Method addURL;
    private static Method defineClass;

    static {
        out.println("MixinLoader by IzzelAliz");
        try {
            addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            addURLAvailable = false;
            try {
                defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
                defineClass.setAccessible(true);
            } catch (NoSuchMethodException e1) {
                e1.printStackTrace();
            }
        }
        try {
            addClassesToClassloader();
            out.println("Successfully add Mixin to classpath!");
        } catch (Exception e) {
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

    private static void extract(String name, String target) throws Exception {
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

    private static void addClassesToClassloader() throws Exception {
        extract("mixin-0.8.jar", "./libraries/org/spongepowered/mixin/0.8/mixin-0.8.jar");
        extract("asm-util-7.2.jar", "./libraries/org/ow2/asm/asm-util/7.2/asm-util-7.2.jar");
        extract("asm-analysis-7.2.jar", "./libraries/org/ow2/asm/asm-analysis/7.2/asm-analysis-7.2.jar");
    }

    private static void load(String file, ClassLoader loader) throws Exception {
        if (addURLAvailable) {
            URL url = Paths.get(file).toUri().toURL();
            addURL.invoke(loader, url);
        }
    }

    private static Class<?> defineClass(ClassLoader loader, String name, byte[] buf) {
        try {
            return (Class<?>) defineClass.invoke(loader, name, buf, 0, buf.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
