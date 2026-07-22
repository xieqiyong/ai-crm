package com.hz.crm.domain.channel;

public enum ChannelStatus {
    NEW,
    WAITING_TRANSCRIPTION,
    TRANSCRIBED,
    WAITING_AI_ANALYSIS,
    ANALYZED,
    PROMOTED
}
