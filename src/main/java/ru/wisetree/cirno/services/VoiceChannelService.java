package ru.wisetree.cirno.services;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.managers.AudioManager;

public interface VoiceChannelService {
    AudioManager joinMemberChannel(Member member);
}
