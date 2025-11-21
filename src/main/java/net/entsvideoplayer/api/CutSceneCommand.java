package net.entsvideoplayer.api;

import java.util.Collection;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.entsvideoplayer.network.CutsceneNetworkServer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class CutSceneCommand {

    private static final SuggestionProvider<ServerCommandSource> TYPE_SUGGESTIONS = (context, builder) -> 
        CommandSource.suggestMatching(new String[]{"url", "local"}, builder);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("playcutscene")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("target", EntityArgumentType.players())
                        .then(CommandManager.argument("type", StringArgumentType.word())
                            .suggests(TYPE_SUGGESTIONS)
                            .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                .executes(context -> {
                                    Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "target");
                                    String type = StringArgumentType.getString(context, "type").toLowerCase();
                                    String file = StringArgumentType.getString(context, "file");

                                    boolean isURL = type.equals("url");
                                    String videoPath = file.replace("\"", "");

                                    // Send to all targeted players
                                    int count = 0;
                                    for (ServerPlayerEntity player : targets) {
                                        sendCutsceneToPlayer(player, videoPath, isURL);
                                        count++;
                                    }

                                    final int finalCount = count;
                                    context.getSource().sendFeedback(
                                        () -> Text.literal("Cutscene sent to " + finalCount + " player(s): " + videoPath), 
                                        false
                                    );
                                    return count;
                                })
                            )
                        )
                    )
            );
        });
    }

    private static void sendCutsceneToPlayer(ServerPlayerEntity player, String videoPath, boolean isURL) {
        // Always send packet to client - works for both singleplayer and multiplayer
        // The packet will be received on the client side regardless of environment
        CutsceneNetworkServer.sendCutscene(player, videoPath, isURL, true, true);
    }
    
}