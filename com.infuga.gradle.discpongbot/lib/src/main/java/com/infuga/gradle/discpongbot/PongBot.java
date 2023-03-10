package com.infuga.gradle.discpongbot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;

public class PongBot {
	
	private static String TOKEN = "MTA1MTQwMTczMDI0NTU0MTg4OA.GRsxh0.RLLsa5zalkgIOHvJn3ibXxwI0ZYdtE1j5N8zJs";
	private static final Map<String, Command> commands = new HashMap<String, Command>();


	
	public static void main(String[] args) {
		// Creates AudioPlayer instances and translates URLs to AudioTrack instances
		final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
		// This is an optimization strategy that Discord4J can utilize. It is not important to understand
		playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
		// Allow playerManager to parse remote sources like YouTube links
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);
		// Create an AudioPlayer so Discord4J can receive audio data
		final AudioPlayer player = playerManager.createPlayer();
		// We will be creating LavaPlayerAudioProvider in the next step
		AudioProvider provider = new LavaPlayerAudioProvider(player);

		GatewayDiscordClient client = DiscordClientBuilder.create(TOKEN)
                .build()
                .login()
                .block();
        
	    commands.put("ping", event -> event.getMessage()
	        .getChannel().block()
	        .createMessage("Pong!").block());
	    commands.put("join", event -> {
	        final Member member = event.getMember().orElse(null);
	        if (member != null) {
	            final VoiceState voiceState = member.getVoiceState().block();
	            if (voiceState != null) {
	                final VoiceChannel channel = voiceState.getChannel().block();
	                if (channel != null) {
	                    // join returns a VoiceConnection which would be required if we were
	                    // adding disconnection features, but for now we are just ignoring it.
	                    channel.join().withProvider(provider).block();
	                }
	            }
	        }
	    });	    

	    commands.put("leave", event -> {
	        final Member member = event.getMember().orElse(null);
	        if (member != null) {
	            final VoiceState voiceState = member.getVoiceState().block();
	            if (voiceState != null) {
	                final VoiceChannel channel = voiceState.getChannel().block();
	                if (channel != null) {
	                	channel.sendDisconnectVoiceState().block();
	                }
	            }
	        }
	    });	    

	    final TrackScheduler scheduler = new TrackScheduler(player);
	    commands.put("play", event -> {
	        final String content = event.getMessage().getContent();
	        final List<String> command = Arrays.asList(content.substring(6).split(","));
	        playerManager.loadItem(command.get(0), scheduler);
	    });

	    commands.put("stop", event -> {
	        player.stopTrack();
	    });

	    commands.put("help", event -> {
	    	StringBuffer sb = new StringBuffer("Available commands are:\n");
	    	commands.keySet().stream().sorted().forEach(key -> sb.append(key + "\n"));
	    	
	    	event.getMessage().getChannel().block()
	        .createMessage(sb.toString()).block();
	    });

	    client.getEventDispatcher().on(ReadyEvent.class)
	        .subscribe(event -> {
	          User self = event.getSelf();
	          System.out.println(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
	        });

        
        client.getEventDispatcher().on(MessageCreateEvent.class)
        // subscribe is like block, in that it will *request* for action
        // to be done, but instead of blocking the thread, waiting for it
        // to finish, it will just execute the results asynchronously.
        .subscribe(event -> {
            final String content = event.getMessage().getContent(); // 3.1 Message.getContent() is a String
            for (final Map.Entry<String, Command> entry : commands.entrySet()) {
                // We will be using ! as our "prefix" to any command in the system.
                if (content.startsWith('!' + entry.getKey())) {
                    entry.getValue().execute(event);
                    break;
                }
            }
        });

        client.onDisconnect().block();
	}
}
