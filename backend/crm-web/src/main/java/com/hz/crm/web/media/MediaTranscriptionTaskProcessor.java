package com.hz.crm.web.media;

import com.hz.crm.application.media.MediaTranscriptionApplicationService;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.media.MediaTranscriptionTaskEntity;
import com.hz.crm.web.attachment.AttachmentStorageService;
import com.hz.crm.web.attachment.AttachmentUploadResponse;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "crm.media.transcription", name = "enabled", havingValue = "true")
public class MediaTranscriptionTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(MediaTranscriptionTaskProcessor.class);

    @Autowired
    private MediaTranscriptionApplicationService mediaTranscriptionApplicationService;

    @Autowired
    private AttachmentStorageService attachmentStorageService;

    @Autowired(required = false)
    private VolcengineAsrClient volcengineAsrClient;

    @Value("${crm.media.transcription.batch-size:10}")
    private int batchSize;

    @Value("${crm.media.transcription.max-retry:3}")
    private int maxRetry;

    @Value("${crm.media.transcription.retry-delay-seconds:60}")
    private int retryDelaySeconds;

    @Value("${crm.media.transcription.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${crm.media.transcription.ffmpeg-timeout-ms:600000}")
    private long ffmpegTimeoutMs;

    private final String processorId = ManagementFactory.getRuntimeMXBean().getName();

    private volatile boolean missingClientLogged;

    @Scheduled(
            fixedDelayString = "${crm.media.transcription.dispatch-delay-ms:30000}",
            initialDelayString = "${crm.media.transcription.initial-delay-ms:10000}")
    public void dispatch() {
        if (volcengineAsrClient == null) {
            if (!missingClientLogged) {
                missingClientLogged = true;
                log.warn("媒体转写已开启，但火山语音转写未开启或配置不完整");
            }
            return;
        }
        List<MediaTranscriptionTaskEntity> tasks =
                mediaTranscriptionApplicationService.listWaitingTasks(batchSize, maxRetry, retryDelaySeconds);
        for (MediaTranscriptionTaskEntity task : tasks) {
            processOne(task);
        }
    }

    private void processOne(MediaTranscriptionTaskEntity task) {
        if (!mediaTranscriptionApplicationService.claim(task, processorId)) {
            return;
        }
        try {
            if (!StringUtils.hasText(task.getProviderTaskId())) {
                prepareAudio(task);
                submit(task);
                return;
            }
            query(task);
        } catch (RuntimeException ex) {
            mediaTranscriptionApplicationService.markFailed(task, ex.getMessage());
            log.warn("媒体转写任务处理失败，taskId={}，followupId={}", task.getId(), task.getBusinessId(), ex);
        }
    }

    private void prepareAudio(MediaTranscriptionTaskEntity task) {
        if (StringUtils.hasText(task.getAudioStorageKey()) && StringUtils.hasText(task.getAudioFileUrl())) {
            return;
        }
        if (!isVideo(task)) {
            String format = resolveFormat(task.getFileName(), task.getFileFormat());
            mediaTranscriptionApplicationService.markReady(
                    task,
                    task.getFileName(),
                    task.getContentType(),
                    task.getFileSize(),
                    task.getStorageKey(),
                    task.getFileUrl(),
                    format);
            task.setAudioFileName(task.getFileName());
            task.setAudioContentType(task.getContentType());
            task.setAudioFileSize(task.getFileSize());
            task.setAudioStorageKey(task.getStorageKey());
            task.setAudioFileUrl(task.getFileUrl());
            task.setAudioFileFormat(format);
            return;
        }
        mediaTranscriptionApplicationService.markExtracting(task);
        Path sourceFile = null;
        Path audioFile = null;
        try {
            sourceFile = downloadToTemp(task);
            audioFile = Files.createTempFile("crm-media-audio-", ".wav");
            extractAudio(sourceFile, audioFile);
            String audioName = buildAudioName(task);
            AttachmentUploadResponse audio = attachmentStorageService.uploadGeneratedFile(
                    task.getTenantId(),
                    audioFile,
                    audioName,
                    "audio/wav",
                    "media/audio");
            mediaTranscriptionApplicationService.markReady(
                    task,
                    audio.getFileName(),
                    audio.getContentType(),
                    audio.getSize(),
                    audio.getStorageKey(),
                    audio.getUrl(),
                    "wav");
            task.setAudioFileName(audio.getFileName());
            task.setAudioContentType(audio.getContentType());
            task.setAudioFileSize(audio.getSize());
            task.setAudioStorageKey(audio.getStorageKey());
            task.setAudioFileUrl(audio.getUrl());
            task.setAudioFileFormat("wav");
        } catch (Exception ex) {
            if (ex instanceof BusinessException) {
                throw (BusinessException) ex;
            }
            throw new BusinessException("MEDIA_TRANSCRIBE_001", "视频抽取音频失败：" + ex.getMessage());
        } finally {
            deleteTemp(sourceFile);
            deleteTemp(audioFile);
        }
    }

    private void submit(MediaTranscriptionTaskEntity task) {
        VolcengineAsrSubmitResult result = volcengineAsrClient.submit(task);
        mediaTranscriptionApplicationService.markSubmitted(
                task,
                result.getProviderTaskId(),
                result.getRequestId(),
                result.getRawResultJson());
    }

    private void query(MediaTranscriptionTaskEntity task) {
        VolcengineAsrQueryResult result = volcengineAsrClient.query(task);
        if (result.isFinished()) {
            mediaTranscriptionApplicationService.markSuccess(
                    task,
                    result.getTranscriptText(),
                    result.getUtterancesJson(),
                    result.getRawResultJson());
            return;
        }
        if (result.isProcessing()) {
            mediaTranscriptionApplicationService.markProcessing(task, result.getRawResultJson());
            return;
        }
        throw new BusinessException("MEDIA_TRANSCRIBE_002", result.getErrorMessage());
    }

    private Path downloadToTemp(MediaTranscriptionTaskEntity task) throws Exception {
        String extension = "." + resolveFormat(task.getFileName(), task.getFileFormat());
        Path sourceFile = Files.createTempFile("crm-media-source-", extension);
        InputStream inputStream = attachmentStorageService.openStoredObject(task.getStorageKey());
        try {
            Files.copy(inputStream, sourceFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            inputStream.close();
        }
        return sourceFile;
    }

    private void extractAudio(Path sourceFile, Path audioFile) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i",
                sourceFile.toString(),
                "-vn",
                "-ar",
                "16000",
                "-ac",
                "1",
                "-f",
                "wav",
                audioFile.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readProcessOutput(process, output);
            }
        });
        reader.setDaemon(true);
        reader.start();
        boolean finished = process.waitFor(ffmpegTimeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException("MEDIA_TRANSCRIBE_003", "视频抽取音频超时");
        }
        reader.join(1000L);
        if (process.exitValue() != 0) {
            throw new BusinessException("MEDIA_TRANSCRIBE_004", "ffmpeg执行失败：" + shrink(output.toString()));
        }
        if (!Files.exists(audioFile) || Files.size(audioFile) <= 0) {
            throw new BusinessException("MEDIA_TRANSCRIBE_005", "视频未抽取到有效音频");
        }
    }

    private void readProcessOutput(Process process, StringBuilder output) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 3000) {
                        output.append(line).append('\n');
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception ex) {
            log.debug("读取ffmpeg输出失败", ex);
        }
    }

    private boolean isVideo(MediaTranscriptionTaskEntity task) {
        String contentType = task.getContentType();
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return true;
        }
        String format = resolveFormat(task.getFileName(), task.getFileFormat());
        return "mp4".equals(format)
                || "mov".equals(format)
                || "mkv".equals(format)
                || "webm".equals(format)
                || "avi".equals(format);
    }

    private String resolveFormat(String fileName, String fileFormat) {
        if (StringUtils.hasText(fileFormat)) {
            String text = fileFormat.trim().toLowerCase(Locale.ROOT);
            return text.startsWith(".") ? text.substring(1) : text;
        }
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "dat";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }

    private String buildAudioName(MediaTranscriptionTaskEntity task) {
        String name = task.getFileName();
        if (!StringUtils.hasText(name)) {
            return "followup-audio.wav";
        }
        int index = name.lastIndexOf(".");
        if (index > 0) {
            return name.substring(0, index) + ".wav";
        }
        return name + ".wav";
    }

    private void deleteTemp(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.debug("清理媒体临时文件失败，path={}", path, ex);
        }
    }

    private String shrink(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > 800 ? text.substring(0, 800) : text;
    }
}
