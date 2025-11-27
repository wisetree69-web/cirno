package ru.wisetree.cirno.services;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;

public class JdaVoiceChannelService implements VoiceChannelService {
    public static final String ERR_NULL_MEMBER = "Member cannot be null (baka-check failed)";
    public static final String ERR_MEMBER_NOT_IN_VOICE = "You must be in a voice channel! ⑨";
    public static final String ERR_COULD_NOT_FIND_CHANNEL = "Could not find voice channel (I'm too strong for this)";

    @Override
    public AudioManager joinMemberChannel(Member member) {
        if (member == null) {
            throw new IllegalArgumentException(ERR_NULL_MEMBER);
        }

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            throw new IllegalStateException(ERR_MEMBER_NOT_IN_VOICE);
        }

        AudioChannelUnion channel = voiceState.getChannel();
        if (channel == null) {
            throw new IllegalStateException(ERR_COULD_NOT_FIND_CHANNEL);
        }

        var guild = member.getGuild();
        var audioManager = guild.getAudioManager();

        audioManager.openAudioConnection(channel);
        return audioManager;
    }
}