package com.elfmcys.yesstevemodel.command;

import com.elfmcys.yesstevemodel.command.subcommands.client.CacheCommand;
import com.elfmcys.yesstevemodel.util.YSMMessageFormatter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;


public class ReimaginedYSMClientCommand {

    public static void registerClientCommands(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> commandDispatcher) {
        LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> root = LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("reimaginedysm")
                .requires(source -> true);

        root.then(CacheCommand.register());

        commandDispatcher.register(root);
    }
}