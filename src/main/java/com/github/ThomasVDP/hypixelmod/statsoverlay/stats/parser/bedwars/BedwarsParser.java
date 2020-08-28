package com.github.ThomasVDP.hypixelmod.statsoverlay.stats.parser.bedwars;

import com.github.ThomasVDP.hypixelmod.statsoverlay.stats.parser.RequestWrapper;
import com.github.ThomasVDP.shadowedLibs.net.hypixel.api.reply.PlayerReply;
import com.github.ThomasVDP.shadowedLibs.net.hypixel.api.reply.StatusReply;
import com.google.common.collect.ComparisonChain;
import com.google.gson.JsonObject;
import com.github.ThomasVDP.hypixelmod.statsoverlay.HypixelStatsOverlayMod;
import com.github.ThomasVDP.hypixelmod.statsoverlay.KeyBindManager;
import com.github.ThomasVDP.hypixelmod.statsoverlay.stats.IGameParser;
import com.github.ThomasVDP.hypixelmod.statsoverlay.util.BwSniperHaxRequester;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.Tuple;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.*;

public class BedwarsParser implements IGameParser
{
    private Comparator<NetworkPlayerInfo> playerComparator = new BedwarsComparator(this);
    private BwSniperHaxRequester sniperHaxRequester = new BwSniperHaxRequester();
    private final BedwarsGuiTabListOverlay bwGuiTabRenderer = new BedwarsGuiTabListOverlay(this);

    /**
     * main storage of the playerdata
     */
    private final Map<UUID, Tuple<RequestWrapper, BedwarsProfile>> playersInList = new HashMap<>();

    private boolean isInLobby = false;

    /**
     * Reflection Fields
     */
    private final Field guiTabOverlayField;

    public BedwarsParser()
    {
        Class<?> guiIngameClass;
        try {
            Class<?> labyMainClazz = Class.forName("net.labymod.main.LabyMod");
            //labymod active
            guiIngameClass = Class.forName("net.labymod.core_implementation.mc18.gui.GuiIngameCustom");
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
            //no labymod active
            guiIngameClass = GuiIngame.class;
        }
        this.guiTabOverlayField = ReflectionHelper.findField(guiIngameClass, "overlayPlayerList", "field_175196_v");
    }

    @Override
    public void onPlayerSwitchWorld(StatusReply statusReply, EntityJoinWorldEvent event)
    {
        /*Scoreboard sb = Minecraft.getMinecraft().theWorld.getScoreboard();
        ScoreObjective objective = sb.getObjectiveInDisplaySlot(1);
        System.out.println("Title: " + objective.getName());
        List<String> lines = ScoreboardUtil.getSidebarLines();
        for (String s : lines) {
            System.out.println(s);
        }*/

        this.isInLobby = statusReply.getSession().getMode().equals("LOBBY"); //change isInLobby default to true
        this.playersInList.clear();
    }

    @Override
    public void onRenderGameOverlayEvent(RenderGameOverlayEvent event)
    {
        if (!(Minecraft.getMinecraft().gameSettings.keyBindPlayerList.isKeyDown() || !KeyBindManager.TAB_KEY_BIND.isKeyDown()))
        {
            this.bwGuiTabRenderer.updatePlayerList(true);

            Scoreboard scoreboard = Minecraft.getMinecraft().theWorld.getScoreboard();
            ScoreObjective scoreObjective = scoreboard.getObjectiveInDisplaySlot(0);
            int width = event.resolution.getScaledWidth();

            if (this.isInLobby) {
                try {
                    ((GuiPlayerTabOverlay)guiTabOverlayField.get(Minecraft.getMinecraft().ingameGUI)).updatePlayerList(true);
                    ((GuiPlayerTabOverlay)guiTabOverlayField.get(Minecraft.getMinecraft().ingameGUI)).renderPlayerlist(width, scoreboard, scoreObjective);
                } catch (IllegalAccessException ex) {
                    //ex.printStackTrace();
                }
                return;
            }

            this.bwGuiTabRenderer.renderPlayerList(width, scoreboard, scoreObjective);
        } else {
            this.bwGuiTabRenderer.updatePlayerList(false);
            try {
                ((GuiPlayerTabOverlay)guiTabOverlayField.get(Minecraft.getMinecraft().ingameGUI)).updatePlayerList(false);
            } catch (IllegalAccessException e) {
                //e.printStackTrace();
            }
        }
    }

    @Override
    public void onChatReceived(ClientChatReceivedEvent event)
    {

    }

    /**
     * Used to collect and process all data of the players in playersInTabList
     *
     * @param playersInTabList the {@link Collection} of players we need to process
     */
    public void gatherPlayers(Collection<NetworkPlayerInfo> playersInTabList)
    {
        NetworkPlayerInfo[] playerInfoList = playersInTabList.toArray(new NetworkPlayerInfo[0]);

        for (int i = 0; i < playerInfoList.length; ++i)
        {
            if (!this.playersInList.containsKey(playerInfoList[i].getGameProfile().getId())) {
                int finalI = i;
                BedwarsProfile bwProfile = new BedwarsProfile();
                RequestWrapper requestWrapper = new RequestWrapper(HypixelStatsOverlayMod.apiContainer.getAPI().handleHypixelAPIRequest(api ->
                        api.getPlayerByUuid(playerInfoList[finalI].getGameProfile().getId())
                ), wrapper -> {
                    JsonObject playerObject = ((PlayerReply)wrapper.getReply()).getPlayer();
                    bwProfile.level = getBwLevel(playerObject);
                    bwProfile.winstreak = getWinStreak(playerObject);
                    bwProfile.wlr = getWinLossRatio(playerObject);
                    bwProfile.fkdr = getFKDR(playerObject);
                    bwProfile.bblr = getBBLR(playerObject);
                });
                this.playersInList.put(playerInfoList[i].getGameProfile().getId(), new Tuple<>(requestWrapper, bwProfile));

                /*this.sniperHaxRequester.sendSniperRequest(playerInfoList[i].getGameProfile().getName()).whenComplete((sniperHaxReply, throwable) -> {
                    if (throwable != null) {
                        throwable.printStackTrace();
                        return;
                    }

                    bwProfile.sniper = sniperHaxReply.isSniper;
                    bwProfile.hax = sniperHaxReply.hax;
                });*/
            }
        }
    }

    static class BedwarsProfile
    {
        public int level = -1;
        public int winstreak = -1;
        public double fkdr = -2;
        public double wlr = -2;
        public double bblr = -2;
        public boolean sniper = false;
        public int hax = 0;
    }

    static class BedwarsComparator implements Comparator<NetworkPlayerInfo>
    {
        private final BedwarsParser bwParser;

        private BedwarsComparator(BedwarsParser bwParser) { this.bwParser = bwParser; }

        @Override
        public int compare(NetworkPlayerInfo o1, NetworkPlayerInfo o2) {
            ScorePlayerTeam scoreplayerteam = o1.getPlayerTeam();
            ScorePlayerTeam scoreplayerteam1 = o2.getPlayerTeam();

            BedwarsProfile bwProfile1 = this.bwParser.playersInList.get(o1.getGameProfile().getId()).getSecond();
            BedwarsProfile bwProfile2 = this.bwParser.playersInList.get(o2.getGameProfile().getId()).getSecond();
            int index1 = (int)(bwProfile1.level * bwProfile1.fkdr * bwProfile1.fkdr);
            int index2 = (int)(bwProfile2.level * bwProfile2.fkdr * bwProfile2.fkdr);
            return ComparisonChain.start()
                    .compareFalseFirst(HypixelStatsOverlayMod.partyManager.getPartyMembers().contains(o1.getGameProfile().getName()), HypixelStatsOverlayMod.partyManager.getPartyMembers().contains(o2.getGameProfile().getName()))
                    .compareTrueFirst(o1.getGameType() != WorldSettings.GameType.SPECTATOR, o2.getGameType() != WorldSettings.GameType.SPECTATOR)
                    .compare(scoreplayerteam != null ? scoreplayerteam.getColorPrefix() : "", scoreplayerteam1 != null ? scoreplayerteam1.getColorPrefix() : "")
                    /*.compareTrueFirst(bwProfile1.hax > 0, bwProfile2.hax > 0) //first get the hackers
                    .compareTrueFirst(bwProfile1.sniper, bwProfile2.sniper) // then get the snipers*/
                    .compareTrueFirst(bwProfile1.fkdr == -3, bwProfile2.fkdr == -3) // then get the nicked players
                    .compare(index2, index1) //swapped values to get highest one first
                    .compare(o1.getGameProfile().getName(), o2.getGameProfile().getName())
                    .result();
        }
    }

    public Comparator<NetworkPlayerInfo> getBwComparator()
    {
        return this.playerComparator;
    }

    public Map<UUID, Tuple<RequestWrapper, BedwarsProfile>> getPlayerDataMap()
    {
        return this.playersInList;
    }

    /**
     * returns the bedwars level of the player
     *
     * @param playerObject the {@link JsonObject} received from {@link com.github.ThomasVDP.hypixelpublicapi.HypixelPublicAPIModLibrary}
     * @return bw level when found, otherwise -1, -2 when nicked
     */
    private static int getBwLevel(JsonObject playerObject)
    {
        if (playerObject == null) return -2;

        if (playerObject.has("achievements")) {
            JsonObject achievementsObject = playerObject.getAsJsonObject("achievements");
            if (achievementsObject.has("bedwars_level")) {
                return achievementsObject.get("bedwars_level").getAsInt();
            }
        }
        return -1;
    }

    /**
     * returns the fkdr of the player
     *
     * @param playerObject the {@link JsonObject} received from {@link com.github.ThomasVDP.hypixelpublicapi.HypixelPublicAPIModLibrary}
     * @return the fkdr when found, -1 when no final deaths, -2 when not found, -3 when nicked
     */
    private static double getFKDR(JsonObject playerObject)
    {
        if (playerObject == null) return -3;

        if (playerObject.has("stats")) {
            JsonObject statsObj = playerObject.getAsJsonObject("stats");
            if (statsObj.has("Bedwars")) {
                JsonObject bwObj = statsObj.getAsJsonObject("Bedwars");
                int fk = bwObj.get("final_kills_bedwars").getAsInt();
                int fd = bwObj.get("final_deaths_bedwars").getAsInt();
                if (fd != 0) {
                    return (double)fk / (double)fd;
                } else {
                    return -1;
                }
            }
        }
        return -2;
    }

    /**
     * returns the current winstreak of the player
     *
     * @param playerObject the {@link JsonObject} received from {@link com.github.ThomasVDP.hypixelpublicapi.HypixelPublicAPIModLibrary}
     * @return the winstreak when found, otherwise -1, -2 when nicked
     */
    private static int getWinStreak(JsonObject playerObject)
    {
        if (playerObject == null) return -2;

        if (playerObject.has("stats")) {
            JsonObject statsObj = playerObject.getAsJsonObject("stats");
            if (statsObj.has("Bedwars")) {
                JsonObject bwObj = statsObj.getAsJsonObject("Bedwars");
                return bwObj.get("winstreak").getAsInt();
            }
        }
        return -1;
    }

    /**
     * returns the win-loss ration of the player
     *
     * @param playerObject the {@link JsonObject} received from {@link com.github.ThomasVDP.hypixelpublicapi.HypixelPublicAPIModLibrary}
     * @return the wlr when found, -1 when no losses, -2 when not found, -3 when nicked
     */
    private static double getWinLossRatio(JsonObject playerObject)
    {
        if (playerObject == null) return -3;

        if (playerObject.has("stats")) {
            JsonObject statsObj = playerObject.getAsJsonObject("stats");
            if (statsObj.has("Bedwars")) {
                JsonObject bwObj = statsObj.getAsJsonObject("Bedwars");
                int wins = bwObj.get("wins_bedwars").getAsInt();
                int losses = bwObj.get("losses_bedwars").getAsInt();
                if (losses != 0) {
                    return (double)wins / (double)losses;
                } else {
                    return -1;
                }
            }
        }
        return -2;
    }

    /**
     * returns the beds-broken-beds-lost-ratio of the player
     *
     * @param playerObject the {@link JsonObject} received from {@link com.github.ThomasVDP.hypixelpublicapi.HypixelPublicAPIModLibrary}
     * @return the bblr when found, -1 when no beds lost, -2 when not found, -3 when nicked
     */
    private static double getBBLR(JsonObject playerObject)
    {
        if (playerObject == null) return -3;

        if (playerObject.has("stats")) {
            JsonObject statsObj = playerObject.getAsJsonObject("stats");
            if (statsObj.has("Bedwars")) {
                JsonObject bwObj = statsObj.getAsJsonObject("Bedwars");
                int bedsBroken = bwObj.get("beds_broken_bedwars").getAsInt();
                int bedsLost = bwObj.get("beds_lost_bedwars").getAsInt();
                if (bedsBroken != 0) {
                    return (double)bedsBroken / (double)bedsLost;
                } else {
                    return -1;
                }
            }
        }
        return -2;
    }
}