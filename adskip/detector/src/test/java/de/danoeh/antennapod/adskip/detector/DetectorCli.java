package de.danoeh.antennapod.adskip.detector;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

public final class DetectorCli {

    private DetectorCli() {
    }

    public static void main(String[] args) throws Exception {
        long declaredDurationMs = Long.parseLong(args[0]);
        String played = args[1];
        List<String> copies = Arrays.asList(args).subList(2, args.length);
        long started = System.nanoTime();
        InMemoryKnownAdLibrary library = new InMemoryKnownAdLibrary();
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(library, false, true,
                Math.min(2, copies.size()), copies.size());
        DetectionResult result = detector.detect(new EpisodeAudio(() -> new FileInputStream(played),
                        index -> new FileInputStream(copies.get(index)), declaredDurationMs));
        System.out.println("took " + (System.nanoTime() - started) / 1_000_000 + " ms");
        for (AdSpan span : result.spans) {
            System.out.println(span + "  (" + span.durationMs() / 1000.0 + " s)");
        }
        System.out.println("copies compared " + result.copiesCompared + ", ads total "
                + AdSpan.totalMs(result.spans) / 1000.0 + " s, unexplained " + result.unexplainedMs / 1000.0 + " s, learned clips " + library.all().size());
        for (KnownAd ad : library.all()) {
            System.out.println("  clip " + ad.id + ": " + ad.hashes.length * 1152 / 44100.0 + " s");
        }
    }
}
