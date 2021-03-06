package me.towdium.jecharacters;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.RootCommandNode;
import me.towdium.jecharacters.JechConfig.Spell;
import me.towdium.jecharacters.utils.Profiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Objects;

import static net.minecraft.util.text.TextFormatting.*;
import static net.minecraft.util.text.event.ClickEvent.Action.SUGGEST_COMMAND;

@Mod.EventBusSubscriber({Dist.CLIENT})
public class JechCommand {
    static CommandDispatcher<ISuggestionProvider> dispatcher;
    static LiteralArgumentBuilder<ISuggestionProvider> builder;

    static {
        builder = literal("jech")
                .executes((c) -> {
                    TextComponent tc = new TranslationTextComponent("jecharacters.chat.help");
                    getPlayer().sendMessage(tc);
                    return 0;
                }).then(literal("profile").executes(c -> profile()))
                .then(literal("verbose")
                        .then(literal("true").executes(c -> {
                            JechConfig.enableVerbose.set(true);
                            return 0;
                        })).then(literal("false").executes(c -> {
                            JechConfig.enableVerbose.set(false);
                            return 0;
                        }))
                ).then(literal("silent")).executes(c -> {
                    JechConfig.enableChat.set(false);
                    return 0;
                }).then(literal("keyboard")
                        .then(literal("QUANPIN").executes(c -> {
                            JechConfig.enumKeyboard.set(Spell.QUANPIN);
                            JechConfig.enableQuote.set(false);
                            return 0;
                        })).then(literal("DAQIAN").executes(c -> {
                            JechConfig.enumKeyboard.set(Spell.DAQIAN);
                            JechConfig.enableQuote.set(true);
                            return 0;
                        })).then(literal("XIAOHE").executes(c -> {
                            JechConfig.enumKeyboard.set(Spell.XIAOHE);
                            JechConfig.enableQuote.set(false);
                            return 0;
                        })).then(literal("ZIRANMA").executes(c -> {
                            JechConfig.enumKeyboard.set(Spell.ZIRANMA);
                            JechConfig.enableQuote.set(false);
                            return 0;
                        })));
        dispatcher = new CommandDispatcher<>();
        dispatcher.register(builder);
    }

    private static LiteralArgumentBuilder<ISuggestionProvider> literal(String s) {
        return LiteralArgumentBuilder.literal(s);
    }

    private static int profile() {
        Thread t = new Thread(() -> {
            ClientPlayerEntity p = getPlayer();
            p.sendMessage(new TranslationTextComponent("jecharacters.chat.start"));
            Profiler.Report r = Profiler.run();
            try (FileOutputStream fos = new FileOutputStream("logs/jecharacters.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(new GsonBuilder().setPrettyPrinting().create().toJson(r));
                osw.flush();
                p.sendMessage(new TranslationTextComponent("jecharacters.chat.saved"));
            } catch (IOException e) {
                p.sendMessage(new TranslationTextComponent("jecharacters.chat.error"));
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return 0;
    }

    @SubscribeEvent
    public static void onOpenGui(GuiScreenEvent.InitGuiEvent event) {
        if (event.getGui() instanceof ChatScreen) {
            RootCommandNode<ISuggestionProvider> root = getPlayer()
                    .connection.getCommandDispatcher().getRoot();
            if (root.getChild("jech") == null) root.addChild(builder.build());
        }
    }

    @SubscribeEvent
    public static void onCommand(ClientChatEvent event) {
        CommandSource cs = getPlayer().getCommandSource();
        String msg = event.getMessage();
        if (msg.startsWith("/jech ") || msg.equals("/jech")) {
            event.setCanceled(true);
            Minecraft.getInstance().ingameGUI.getChatGUI().addToSentMessages(msg);

            try {
                StringReader stringreader = new StringReader(msg);
                if (stringreader.canRead() && stringreader.peek() == '/') stringreader.skip();
                ParseResults<ISuggestionProvider> parse = dispatcher.parse(stringreader, cs);
                dispatcher.execute(parse);
            } catch (CommandSyntaxException e) {
                // copied and modified from net.minecraft.command.Commands
                cs.sendErrorMessage(TextComponentUtils.toTextComponent(e.getRawMessage()));
                if (e.getInput() != null && e.getCursor() >= 0) {
                    int k = Math.min(e.getInput().length(), e.getCursor());
                    ITextComponent tc1 = new StringTextComponent("").applyTextStyle(GRAY).applyTextStyle(i ->
                            i.setClickEvent(new ClickEvent(SUGGEST_COMMAND, event.getMessage())));
                    if (k > 10) tc1.appendText("...");
                    tc1.appendText(e.getInput().substring(Math.max(0, k - 10), k));
                    if (k < e.getInput().length()) {
                        ITextComponent tc2 = (new StringTextComponent(e.getInput().substring(k)))
                                .applyTextStyles(RED, UNDERLINE);
                        tc1.appendSibling(tc2);
                    }
                    tc1.appendSibling((new TranslationTextComponent("command.context.here"))
                            .applyTextStyles(RED, ITALIC));
                    cs.sendErrorMessage(tc1);
                }
            }
        }
    }

    private static ClientPlayerEntity getPlayer() {
        return Objects.requireNonNull(Minecraft.getInstance().player);
    }
}
