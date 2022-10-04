package src.main.java;

public class VideoConverter {

    /*NO TRANSCODING IMPLEMENTED SO FOR SOME FLV FILES THIS WILL THROW AN ERROR:
    [mp4 @ 000001f00d89e880] Could not find tag for codec flv1 in stream #1, codec not currently supported in container
    h264 to h265 REQUIRED

    CHECK THIS TUTORIAL FOR TRANSCODING
    https://github.com/leandromoreira/ffmpeg-libav-tutorial#chapter-3---transcoding*/
    public static void main(String[] args){
        String inputPath = "./data/nightcall.flv";
        String outputPath = inputPath.replace("flv","mp4");
        ConverterEngine converterEngine = new ConverterEngine(inputPath, outputPath);
        converterEngine.startConvert();
    }
}
