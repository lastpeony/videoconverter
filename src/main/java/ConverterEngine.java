package src.main.java;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.PointerPointer;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class ConverterEngine {
    private String inputFilePath;
    private String outputFilePath;

    //AVFormatContext is an object which will hold information about the format.(container)
    private AVFormatContext inputFormatContext;
    private AVFormatContext outputFormatContext;


    public ConverterEngine(String inputFilePath, String outputFilePath) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
    }

    private void readyInput() {
        inputFormatContext = new AVFormatContext(null);

        //open input file and fill inputFormatContext object.
        int openRes = avformat_open_input(inputFormatContext, inputFilePath, null, null);
        if (openRes < 0) { // negative means unsuccesful;
            throw new RuntimeException("Error opening input file.");
        }

        //read packet data to inputFormatContext object.
        int gatherStreamRes = avformat_find_stream_info(inputFormatContext, (PointerPointer) null);
        if (gatherStreamRes < 0) {
            throw new RuntimeException("Error gathering input file stream.");
        }

        //utility function which prints information about input.
        av_dump_format(inputFormatContext, 0, inputFilePath, 0);
        System.out.println("Input format:"+ inputFormatContext.iformat().long_name().getString()+" Input duration:"+inputFormatContext.duration());
    }


    private void readyOutput() {
        outputFormatContext = new AVFormatContext(null);

        //read output file data into outputFormatContext object.
        //if 2nd parameter output format is null ffmpeg takes it from file path. 3rd parameter is format name.
        int openRes = avformat_alloc_output_context2(outputFormatContext, null, null, outputFilePath);
        if (openRes < 0) { // negative means unsuccesfull
            throw new RuntimeException("Error while allocating mem for output file.");
        }

        for (int i = 0; i < inputFormatContext.nb_streams(); i++) {
            AVStream inputStream = inputFormatContext.streams(i);
            //make a new stream to be used for output.
            AVStream outputStream = avformat_new_stream(outputFormatContext, new AVCodec(null));

            //find the registered decoder for the codec id and return an AVCodec,
            //the component that knows how to encode and decode the stream.
            AVCodecParameters inputCodecParameters = inputStream.codecpar();
            AVCodec decoder = avcodec_find_decoder(inputCodecParameters.codec_id());

            AVCodecContext codecContext = avcodec_alloc_context3(decoder);
            if (codecContext.codec_type() == AVMEDIA_TYPE_VIDEO || codecContext.codec_type() == AVMEDIA_TYPE_AUDIO) {
                // copy AVCodecParameters of input to output if stream is video or audio.
                avcodec_parameters_copy(outputStream.codecpar(), inputStream.codecpar());

                //parameter copy does not copy time info. also need to set each frames time_base. this is seconds.
                outputStream.time_base(inputStream.time_base());

                System.out.println("Codec Name:"+decoder.long_name()+" Codec ID:"+decoder.id()+" Bit Rate:"+inputCodecParameters.bit_rate());
            }
        }
        //after copying from input to output print output info.
        av_dump_format(outputFormatContext, 0, outputFilePath, 1);
        System.out.println("Output format:"+ outputFormatContext.oformat().long_name().getString());

        //create avioContext object for output. this will be filled by avio_open.
        AVIOContext avioContext = new AVIOContext();

        //open in write mode.
        avio_open(avioContext, outputFilePath, AVIO_FLAG_WRITE);

        //HORRIBLE NAMING IMO. SET PACKET BUFFER. should be renamed to setPacketBuffer();
        outputFormatContext.pb(avioContext);
    }

    public void startConvert() {
        readyInput();
        readyOutput();
        startMuxing();
    }


    //combine everything into a single package , which is mp4.
    private void startMuxing() {
        //acording to documentation this function should be called before muxing process.
        avformat_alloc_context();

        //muxer function. this function takes encoded data in form of AVPackets.
        //this function must always be called before writing AVPackets.
        avformat_write_header(outputFormatContext, (AVDictionary) null);

        AVPacket packet = new AVPacket();

        /*
        for each frame:
            read it from the demuxer(av_read_frame is a demuxer function.)
            convert timestamps from demuxer to decoder timebases use av_packet_rescale_ts
            write packet to an output media file ensuring correct interleaving.
        write packets to outputFormatContext and free them.

        We're getting the AVPacket's from the decoder,
        adjusting the timestamps, and write the packet properly to the output file
        */
        while (av_read_frame(inputFormatContext, packet) >= 0) {

            int streamIndex = packet.stream_index();

            //To put it simply, FFmpeg's timebase is a mechanism for converting to integers when a stamp is fractional and for a better hierarchical structure.
            av_packet_rescale_ts(packet, inputFormatContext.streams(streamIndex).time_base(),
                    outputFormatContext.streams(streamIndex).time_base());

            av_interleaved_write_frame(outputFormatContext, packet);
        }

        //write packets and free them.
        av_write_trailer(outputFormatContext);
    }
}
