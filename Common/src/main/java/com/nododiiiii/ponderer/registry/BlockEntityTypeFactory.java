package com.nododiiiii.ponderer.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

final class BlockEntityTypeFactory {

    private static final Class<?> SUPPLIER_CLASS;
    private static final Method BUILDER_OF;
    private static final Method BUILDER_BUILD;

    static {
        try {
            Class<?> builderClass = BlockEntityType.Builder.class;
            BUILDER_OF = findBuilderOf(builderClass);
            SUPPLIER_CLASS = BUILDER_OF.getParameterTypes()[0];
            BUILDER_BUILD = findBuilderBuild(builderClass);
            BUILDER_OF.setAccessible(true);
            BUILDER_BUILD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private BlockEntityTypeFactory() {
    }

    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> BlockEntityType<T> create(BiFunction<BlockPos, BlockState, T> factory, Block... validBlocks) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (args != null && args.length == 2 && args[0] instanceof BlockPos pos && args[1] instanceof BlockState state) {
                return factory.apply(pos, state);
            }
            return switch (method.getName()) {
                case "toString" -> "PondererBlockEntitySupplier";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args == null ? null : args[0]);
                default -> throw new UnsupportedOperationException(method.toString());
            };
        };

        Object supplier = Proxy.newProxyInstance(
            BlockEntityTypeFactory.class.getClassLoader(),
            new Class<?>[]{SUPPLIER_CLASS},
            handler);

        try {
            Object builder = BUILDER_OF.invoke(null, supplier, validBlocks);
            return (BlockEntityType<T>) BUILDER_BUILD.invoke(builder, new Object[]{null});
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create block entity type", e);
        }
    }

    private static Method findBuilderOf(Class<?> builderClass) throws NoSuchMethodException {
        for (Method method : builderClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!builderClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 2
                && !parameterTypes[0].isArray()
                && parameterTypes[1].isArray()
                && parameterTypes[1].getComponentType() == Block.class) {
                return method;
            }
        }
        throw new NoSuchMethodException("BlockEntityType.Builder factory method");
    }

    private static Method findBuilderBuild(Class<?> builderClass) throws NoSuchMethodException {
        for (Method method : builderClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!BlockEntityType.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException("BlockEntityType.Builder build method");
    }
}
