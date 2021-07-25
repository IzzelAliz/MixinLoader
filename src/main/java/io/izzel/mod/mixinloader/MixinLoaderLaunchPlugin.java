package io.izzel.mod.mixinloader;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.service.modlauncher.MixinServiceModLauncher;

import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import static io.izzel.mod.mixinloader.MixinLoaderService.UNSAFE;

public record MixinLoaderLaunchPlugin(ILaunchPluginService delegate) implements ILaunchPluginService {

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return delegate.handlesClass(classType, isEmpty);
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        return delegate.handlesClass(classType, isEmpty, reason);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        return delegate.processClass(phase, classNode, classType);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        return delegate.processClass(phase, classNode, classType, reason);
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        return delegate.processClassWithFlags(phase, classNode, classType, reason);
    }

    @Override
    public void offerResource(Path resource, String name) {
        delegate.offerResource(resource, name);
    }

    @Override
    public void addResources(List<SecureJar> resources) {
        try {
            var serviceField = delegate.getClass().getDeclaredField("service");
            var service = (MixinServiceModLauncher) UNSAFE.getObject(delegate, UNSAFE.objectFieldOffset(serviceField));
            for (SecureJar jar : resources) {
                service.getPrimaryContainer().add(new ContainerHandleSecureJar(jar));
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
        try {
            var handle = MixinLoaderService.LOOKUP.findVirtual(delegate.getClass(), "initializeLaunch", MethodType.methodType(void.class, ITransformerLoader.class, Path[].class));
            handle.invokeWithArguments(delegate, transformerLoader, null);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T getExtension() {
        return delegate.getExtension();
    }

    @Override
    public void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
        delegate.customAuditConsumer(className, auditDataAcceptor);
    }

    public record ContainerHandleSecureJar(SecureJar jar) implements IContainerHandle {

        @Override
        public String getAttribute(String name) {
            return jar.getManifest().getMainAttributes().getValue(name);
        }

        @Override
        public Collection<IContainerHandle> getNestedContainers() {
            return Collections.emptyList();
        }
    }
}
