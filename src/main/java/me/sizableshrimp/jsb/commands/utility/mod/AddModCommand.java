/*
 * Copyright (c) 2021 SizableShrimp
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.sizableshrimp.jsb.commands.utility.mod;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import me.sizableshrimp.jsb.Bot;
import me.sizableshrimp.jsb.api.CommandContext;
import me.sizableshrimp.jsb.api.CommandInfo;
import me.sizableshrimp.jsb.api.ConfirmationCommand;
import me.sizableshrimp.jsb.args.Args;
import me.sizableshrimp.jsb.data.BaseConfirmationContext;
import me.sizableshrimp.jsb.data.Mod;
import me.sizableshrimp.jsb.util.MessageUtil;
import me.sizableshrimp.jsb.util.Reactions;
import org.fastily.jwiki.core.AReply;
import org.fastily.jwiki.core.Wiki;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class AddModCommand extends ConfirmationCommand<AddModCommand.ConfirmationContext> {
    private static final Pattern CHARACTERS = Pattern.compile("^([a-zA-Z0-9]+)$");

    public AddModCommand() {
        super(Map.of(
                Reactions.CHECKMARK, (confirmation, event) -> {
                    Mod newMod = confirmation.newMod;

                    try {
                        AReply reply = newMod.add();
                        String message = MessageUtil.getMessageFromReply(reply, r -> {
                            Bot.LOGGER.info("Added mod {} to mods list.", newMod);
                            return String.format("Added **%s** (abbreviated as `%s`) to the mods list. Please mark for translation.", newMod.getName(), newMod.getAbbrv());
                        }, r -> "adding mod " + newMod.getAbbrv());

                        return event.getChannel().flatMap(channel -> MessageUtil.sendMessage(message, channel));
                    } catch (IllegalStateException e) {
                        return event.getChannel().flatMap(channel -> MessageUtil.sendMessage(e.getMessage(), channel));
                    }
                }, Reactions.X, (confirmation, event) -> event.getMessage().flatMap(Message::delete)
                        .then(event.getChannel().flatMap(channel -> MessageUtil.sendMessage(String.format("Cancelling addition of mod named **%s** with abbreviation `%s`...",
                                confirmation.newMod.getName(), confirmation.newMod.getAbbrv()), channel)))
        ), List.of(Reactions.CHECKMARK, Reactions.X));
    }

    @Override
    public CommandInfo getInfo() {
        return new CommandInfo(this, "%cmdname% <mod abbreviation> <mod name> [mod page]", """
                Adds a mod to the mod list which takes a mod abbreviation and the unlocalized mod name.
                Optionally takes a mod page link if it differs from the unlocalized mod name.
                Wrap any arguments with spaces in quotes.
                """);
    }

    @Override
    public String getName() {
        return "addmod";
    }

    @Override
    public Set<String> getAliases() {
        return Set.of("addabbrv");
    }

    @Override
    public List<String> getRequiredRoles() {
        return List.of("Editor");
    }

    @Override
    public Mono<Message> run(CommandContext context, MessageCreateEvent event, Args args) {
        if (args.getLength() < 2 || args.getLength() > 3) {
            return incorrectUsage(event);
        }

        return event.getMessage().getChannel().flatMap(channel -> {
            String modAbbrv = args.getArg(0).toUpperCase();
            String modName = args.getArg(1);
            String modPage = args.getArgNullable(2);
            if (!CHARACTERS.matcher(modAbbrv).matches()) {
                return sendMessage(
                        "Abbreviations that are not purely alphanumeric are not supported for technical reasons.",
                        channel);
            }
            if (Character.isDigit(modAbbrv.charAt(0))) {
                return sendMessage("Mod abbreviation cannot start with a number!", channel);
            }

            Mod newMod = new Mod(context.getWiki(), modAbbrv, modName, modPage);
            Mod conflict = newMod.getConflict();
            if (conflict != null) {
                return sendMessage(
                        String.format("This mod abbreviation already exists as **%s** with abbreviation `%s`!",
                                conflict.getName(), conflict.getAbbrv()),
                        channel);
            } else {
                Mono<Message> confirm;
                if (newMod.hasDistinctLink()) {
                    confirm = sendMessage(String.format(
                            "Do you want to add a new mod to the list named **%s** with abbreviation `%s` and link `%s`? Please react with ✅ for yes or ❌ for no.",
                            newMod.getName(), newMod.getAbbrv(), newMod.getUrlLink()), channel);
                } else {
                    confirm = sendMessage(String.format(
                            "Do you want to add a new mod to the list named **%s** with abbreviation `%s`? Please react with ✅ for yes or ❌ for no.",
                            newMod.getName(), newMod.getAbbrv()), channel);
                }
                return confirm.flatMap(m -> addReactions(m, new ConfirmationContext(context.getWiki(), event.getMessage(), m, newMod)));
            }
        });
    }

    public static final class ConfirmationContext extends BaseConfirmationContext {
        public final Mod newMod;

        public ConfirmationContext(Wiki wiki, Message original, Message response, Mod newMod) {
            super(wiki, original, response);
            this.newMod = newMod;
        }
    }
}