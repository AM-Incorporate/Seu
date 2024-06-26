package com.amincorporate.seu.work;

import com.amincorporate.seu.dto.CoinListDTO;
import com.amincorporate.seu.dto.ExchangeInfoDTO;
import com.amincorporate.seu.dto.WalletCreateDTO;
import com.amincorporate.seu.dto.WalletInfoDTO;
import com.amincorporate.seu.entity.wallet.WalletType;
import com.amincorporate.seu.exception.MemberNoExistsException;
import com.amincorporate.seu.exception.WalletNoExistsException;
import com.amincorporate.seu.pallet.NoticePallet;
import com.amincorporate.seu.service.*;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class WalletMessageWork {

    private final WalletService walletService;
    private final MemberService memberService;
    private final TradeService tradeService;

    private static String[] createWalletCommands = {"createWallet", "지갑생성", "지갑만들기"};
    private static String[] deleteWalletCommands = {"deleteWallet", "지갑삭제"};
    private static String[] walletInfoCommands = {"walletInfo", "지갑정보", "지갑조회", "내지갑"};

    public boolean isWalletMessageCommand(String command) {
        if (isCreateWalletCommand(command)) return true;
        if (isDeleteWalletCommand(command)) return true;
        if (isWalletInfoCommand(command)) return true;
        return false;
    }

    public boolean isCreateWalletCommand(String command) {
        for (String createWalletCommand : createWalletCommands) {
            if (command.equals(createWalletCommand)) return true;
        }
        return false;
    }

    public boolean isDeleteWalletCommand(String command) {
        for (String deleteWalletCommand : deleteWalletCommands) {
            if (command.equals(deleteWalletCommand)) return true;
        }
        return false;
    }

    public boolean isWalletInfoCommand(String command) {
        for (String walletInfoCommand : walletInfoCommands) {
            if (command.equals(walletInfoCommand)) return true;
        }
        return false;
    }

    public void createWallet(MessageReceivedEvent event, JDA jda) {
        if (!memberService.isMemberExists(event.getAuthor().getId())) {
            sendErrorMessage("지갑 생성 실패",
                    event.getAuthor().getName() + "님은 가입되지 않았습니다! 먼저 \"스우 가입\" 명령어를 통해 가입해 주세요.",
                    event);
            return;
        }
        if (!event.isFromGuild()) {
            sendErrorMessage("지갑 생성 실패",
                    "DM 말고 채널에서 사용해주세요.",
                    event);
            return;
        }

        RichCustomEmoji copperIconRaw = event.getGuild().getEmojisByName("copper", true).getFirst();
        RichCustomEmoji bauxiteIconRaw = event.getGuild().getEmojisByName("bauxite", true).getFirst();
        RichCustomEmoji uraniumIconRaw = event.getGuild().getEmojisByName("uranium", true).getFirst();

        String copperIcon = event.getGuild().getEmojisByName("copper", true).getFirst().getFormatted();
        String bauxiteIcon = event.getGuild().getEmojisByName("bauxite", true).getFirst().getFormatted();
        String uraniumIcon = event.getGuild().getEmojisByName("uranium", true).getFirst().getFormatted();

        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle(":moneybag: 지갑을 골라주세요")
                .setColor(NoticePallet.warningYellow)
                .setDescription("**" + WalletType.COPPER.toString() + " (**" + copperIcon + "**) : 1배**\n" +
                        "**" + WalletType.BAUXITE.toString() + " (**" + bauxiteIcon + "**) : 2배**\n" +
                        "**" + WalletType.URANIUM.toString() + " (**" + uraniumIcon + "**) : 3배**")
                .build()).queue(message -> {
            message.addReaction(copperIconRaw).queue();
            message.addReaction(bauxiteIconRaw).queue();
            message.addReaction(uraniumIconRaw).queue();

            jda.addEventListener(new ListenerAdapter() {
                @Override
                public void onMessageReactionAdd(MessageReactionAddEvent reactionEvent) {

                    if (!message.getId().equals(reactionEvent.getMessageId()) || !reactionEvent.getUserId().equals(event.getAuthor().getId())) {
                        return;
                    }

                    String selectEmoji = reactionEvent.getEmoji().getAsReactionCode().split(":")[0];

                    try {

                        WalletCreateDTO walletCreateDTO = walletService.create(event.getAuthor().getId(), WalletType.valueOf(selectEmoji.toUpperCase()));

                        String icon = "";
                        if (walletCreateDTO.getWalletType() == WalletType.COPPER) icon = copperIcon;
                        else if (walletCreateDTO.getWalletType() == WalletType.BAUXITE) icon = bauxiteIcon;
                        else if (walletCreateDTO.getWalletType() == WalletType.URANIUM) icon = uraniumIcon;

                        editSuccessMessage("지갑 생성 성공!",
                                "> 지갑주소: **" + walletCreateDTO.getWalletId() + "**\n> 지갑종류: **" + walletCreateDTO.getWalletType().toString() + " (**" + icon + "**)**",
                                message);

                        message.clearReactions().queue();

                    } catch (Exception e) {
                        sendErrorMessage("원인을 모르는 지갑 생성 실패",
                                "원인을 모르는 문제가 다음의 쪽지만 남겨놓고 갔습니다.\n" + e.getMessage(),
                                event);
                        e.printStackTrace();
                    }
                }
            });
        });

    }

    public void walletInfo(MessageReceivedEvent event) {
        String[] userInput = event.getMessage().getContentDisplay().strip().split(" ");
        String accountListString = "";
        if (event.isFromGuild()) {
            userInput = Arrays.copyOfRange(userInput, 1, userInput.length);
        }

        if (userInput.length == 1) { // 지갑정보만 입력함
            String copperIcon = event.getGuild().getEmojisByName("copper", true).getFirst().getFormatted();
            String bauxiteIcon = event.getGuild().getEmojisByName("bauxite", true).getFirst().getFormatted();
            String uraniumIcon = event.getGuild().getEmojisByName("uranium", true).getFirst().getFormatted();
            try {
                List<WalletInfoDTO> walletInfoDTOS = walletService.getInfo(event.getAuthor().getId());

                for (WalletInfoDTO walletInfoDTO : walletInfoDTOS) {

                    String icon = "";
                    if (walletInfoDTO.getWalletType() == WalletType.COPPER) icon = copperIcon;
                    else if (walletInfoDTO.getWalletType() == WalletType.BAUXITE) icon = bauxiteIcon;
                    else if (walletInfoDTO.getWalletType() == WalletType.URANIUM) icon = uraniumIcon;

                    accountListString += "> 지갑주소: **" + walletInfoDTO.getId() + "**\n> 지갑종류: **" + walletInfoDTO.getWalletType().toString() + " (**" + icon + "**)**\n\n";
                }

                sendSuccessMessage(event.getAuthor().getName() + "님의 지갑 목록",
                        accountListString,
                        event);
            } catch (MemberNoExistsException e) {
                sendErrorMessage("지갑 조회 실패",
                        event.getAuthor().getName() + "님은 Seu에 가입되어 있지 않아요.\n\n\"스우 가입\" 으로 가입 해주세요.",
                        event);
            } catch (WalletNoExistsException e) {
                sendErrorMessage("지갑 조회 실패",
                        event.getAuthor().getName() + "님은 지갑이 존재하지 않아요.\n\n\"스우 지갑생성\" 으로 지갑을 만들어주세요.",
                        event);
            } catch (Exception e) {
                sendErrorMessage("원인을 모르는 지갑 조회 실패",
                        "원인을 모르는 문제가 다음의 쪽지만 남겨놓고 갔습니다.\n" + e.getMessage(),
                        event);
                e.printStackTrace();
            }
            return;
        } else {
            userInput = Arrays.copyOfRange(userInput, 1, userInput.length);
        }
        try {
            List<ExchangeInfoDTO> exchangeInfoDTOS = walletService.getInfoDetail(event.getAuthor().getId(), userInput[0]);
            String walletDetail = "";

            RichCustomEmoji BTCIcon = event.getGuild().getEmojisByName("BTC", true).getFirst();
            RichCustomEmoji ETHIcon = event.getGuild().getEmojisByName("ETH", true).getFirst();
            RichCustomEmoji SOLIcon = event.getGuild().getEmojisByName("SOL", true).getFirst();
            RichCustomEmoji ADAIcon = event.getGuild().getEmojisByName("ADA", true).getFirst();
            RichCustomEmoji DOGEIcon = event.getGuild().getEmojisByName("DOGE", true).getFirst();

            Map<String, RichCustomEmoji> coinIcons = new HashMap<>() {{
                put("BTC", BTCIcon);
                put("ETH", ETHIcon);
                put("SOL", SOLIcon);
                put("ADA", ADAIcon);
                put("DOGE", DOGEIcon);
            }};

            Map<String, String> coins = new HashMap<>() {{
                put("Bitcoin", "BTC");
                put("Dogecoin", "DOGE");
                put("Ethereum", "ETH");
                put("Solana", "SOL");
                put("Cardano", "ADA");
            }};

            for (ExchangeInfoDTO exchangeInfoDTO : exchangeInfoDTOS) {
                String coinName = exchangeInfoDTO.getName();
                Double coinQT = exchangeInfoDTO.getQuantity();
                String coinSymbol = exchangeInfoDTO.getSymbol();
                Double coinPrice = exchangeInfoDTO.getPrice();
                Double coinCurrentPrice = tradeService.getCoin(coins.get(coinName)).getPrice();

                walletDetail += "> " + coinIcons.get(coins.get(coinName)).getFormatted() +
                        " **" + coinName + " (" + coins.get(coinName) + ")**\n> **수량: " +
                        decimalCutter(coinQT, exchangeInfoDTO.getMaxDecimal()) + " " +
                        coinSymbol + "**\n> **1.0 단위당 구매가치: " + decimalCutter(coinPrice, 2) +
                        " $**\n> **   └─1.0 단위당 현재가치─> " + decimalCutter(coinCurrentPrice, 2) +
                        " $**\n> **수익률: " + decimalCutter(((coinCurrentPrice / coinPrice) - 1) * 100,2) + "%**\n\n";
            }

            sendSuccessMessage(userInput[0] + "지갑의 내용물들",
                    walletDetail,
                    event);

        } catch (WalletNoExistsException e) {
            sendErrorMessage("지갑 상세조회 실패",
                    userInput[0] + "이란 주소를 가진 지갑은 존재하지 않거나, " + event.getAuthor().getName() + "님이 소유하고 있지 않습니다.",
                    event);
        } catch (Exception e) {
            sendErrorMessage("원인을 모르는 지갑 상세조회 실패",
                    "원인을 모르는 문제가 다음의 쪽지만 남겨놓고 갔습니다.\n" + e.getMessage(),
                    event);
            e.printStackTrace();
        }

    }

    private String decimalCutter(Double value, int cutDecimal) {
        String decimal = String.valueOf(value).split("\\.")[1];
        int decimalLen = decimal.length();
        if (decimalLen > cutDecimal) decimalLen = cutDecimal;
        return String.format("%." + decimalLen + "f", value);
    }

    private void sendSuccessMessage(String title, String description, MessageReceivedEvent event) {
        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle(":smile: **" + title + "**")
                .setColor(NoticePallet.goodGreen)
                .setDescription(description)
                .build()).queue();
    }

    private void sendErrorMessage(String title, String description, MessageReceivedEvent event) {
        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle(":frowning: **" + title + "**")
                .setColor(NoticePallet.badRed)
                .setDescription(description)
                .build()).queue();
    }

    private void sendWarningMessage(String title, String description, MessageReceivedEvent event) {
        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle(":face_with_raised_eyebrow: **" + title + "**")
                .setColor(NoticePallet.warningYellow)
                .setDescription(description)
                .build()).queue();
    }

    private void editSuccessMessage(String title, String description, Message message) {
        message.editMessageEmbeds(new EmbedBuilder()
                .setTitle(":smile: **" + title + "**")
                .setDescription(description)
                .setColor(NoticePallet.goodGreen)
                .build()).queue();
    }

    private void editErrorMessage(String title, String description, Message message) {
        message.editMessageEmbeds(new EmbedBuilder()
                .setTitle(":frowning: **" + title + "**")
                .setDescription(description)
                .setColor(NoticePallet.badRed)
                .build()).queue();
    }

    private void editWarningMessage(String title, String description, MessageChannel channel, String messageID) {
        channel.editMessageEmbedsById(messageID, new EmbedBuilder()
                .setTitle(":face_with_raised_eyebrow: **" + title + "**")
                .setDescription(description)
                .setColor(NoticePallet.warningYellow)
                .build()).queue();
    }
}
