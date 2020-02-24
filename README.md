# MixinLoader

Add a mixin environment to ModLoader using tricky hacks.

## How to use

1. Download latest version in release page.
2. Drop it into `mods` folder.
3. Launch Minecraft.

## Note

There is a problem in Forge's `eventbus` library prevents Mixin working properly, which is detailed in [this issue](https://github.com/SpongePowered/Mixin/issues/369).
This affects Forge under Minecraft 1.15(so far).

If you encountered errors like `org.spongepowered.asm.mixin.transformer.throwables.ReEntrantTransformerError: Re-entrance error.`:

1. Download new eventbus library at https://files.minecraftforge.net/maven/net/minecraftforge/eventbus/2.0.0-milestone.1/eventbus-2.0.0-milestone.1-service.jar
2. Replace `.minecraft/libraries/net/minecraftforge/eventbus/<version>/eventbus-<version>.jar` with the updated jar.

## License

This project is licensed under the MIT License, which can be found at [here](https://github.com/IzzelAliz/MixinLoader/blob/master/LICENSE).
