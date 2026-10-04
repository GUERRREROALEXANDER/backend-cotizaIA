package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a channel to its {@link BriefSource} adapter. The registry is
 * built from every {@code BriefSource} bean Spring knows about, so adapters are
 * discovered, not listed: adding a channel means adding a bean, not editing
 * this class.
 */
public final class BriefSourceRegistry {

    private final Map<BriefChannel, BriefSource> byChannel = new EnumMap<>(BriefChannel.class);

    public BriefSourceRegistry(List<BriefSource> sources) {
        for (BriefSource source : sources) {
            BriefSource previous = byChannel.putIfAbsent(source.channel(), source);
            if (previous != null) {
                throw new IllegalStateException("Duplicate BriefSource for channel " + source.channel());
            }
        }
    }

    /**
     * @throws UnsupportedChannelException when no adapter is registered for the
     *         channel, i.e. the channel exists but has no implementation yet.
     */
    public BriefSource forChannel(BriefChannel channel) {
        BriefSource source = byChannel.get(channel);
        if (source == null) {
            throw new UnsupportedChannelException(channel);
        }
        return source;
    }
}
