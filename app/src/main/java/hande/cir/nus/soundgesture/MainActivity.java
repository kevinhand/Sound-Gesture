package hande.cir.nus.soundgesture;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.hermit.dsp.FFTTransformer;
import org.hermit.dsp.Window;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity implements RecBufListener{

    private Timer timer = new Timer();
    private TimerTask task;
    private Handler handler;
//    private Handler SoundHandler = new Handler();;
    private String title = "Signal Strength";
    private XYSeries[] series = new XYSeries[]{new XYSeries("16k"),new XYSeries("16k"),new XYSeries("16k"),new XYSeries("16k")};
//    private XYSeries series2;
    private XYSeries series3;
    private XYSeries series4;
    private XYMultipleSeriesDataset mDataset;
    private XYMultipleSeriesDataset mDataset2;
    private GraphicalView chart1;
    private GraphicalView chart2;
    private GraphicalView chart3;
    private XYMultipleSeriesRenderer renderer;
    private XYMultipleSeriesRenderer renderer2;
    private Context context;
    private int[] addX = new int[]{0,0,0,0};
    private double[] addY = new double[]{0,0,0,0};
    private TextView Textbox;

    private FFTTransformer spectrumAnalyserTop;
    private FFTTransformer spectrumAnalyserBottom;
    // Analyzed audio spectrum data; history data for each frequency
    // in the spectrum; index into the history data
    private float[] spectrumDataTop;
    private float[][] spectrumHistTop;
    private int spectrumIndexTop;
    private float[] spectrumDataBottom;
    private float[][] spectrumHistBottom;
    private int spectrumIndexBottom;
    private Window.Function windowFunction = Window.Function.BLACKMAN_HARRIS;

    // The desired histogram averaging window. 1 means no averaging.
    private int historyLen = 3;

    // Buffered audio data, and sequence number of the latest block.
    private short[] audioData;
    private long audioSequence = 0;

    // Sequence number of the last block we processed.
    private long audioProcessed = 0;

    // Our audio input device.
    public final static int AUDIO_SAMPLE_RATE = 44100;  //44.1KHz,普遍使用的频率
    public final static int AUDIO_SAMPLE_RATE2 = 48000;
    private static double  mfrequency = 18000;
    private int inputBlockSize =4096;
    private double magnitude =0;
    private double magnitude2 =0;

    int[][] xv = new int[6][100];
    double[][] yv = new double[6][100];

    int spectrumsize=100;
    double[] xv3 = new double[spectrumsize];
    double[] yv3 = new double[spectrumsize];

    double[] xv4 = new double[spectrumsize];
    double[] yv4 = new double[spectrumsize];

    Thread soundPlay;
    boolean isRunning = true;
    private Thread recordingThread;
    private RecBuffer mBuffer;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Textbox = (TextView)findViewById(R.id.result);
        context = getApplicationContext();
        spectrumAnalyserTop = new FFTTransformer(inputBlockSize, windowFunction);
        spectrumAnalyserBottom = new FFTTransformer(inputBlockSize, windowFunction);

        spectrumDataTop = new float[inputBlockSize / 2];
        spectrumHistTop = new float[inputBlockSize / 2][historyLen];
        spectrumIndexTop = 0;
        spectrumDataBottom = new float[inputBlockSize / 2];
        spectrumHistBottom = new float[inputBlockSize / 2][historyLen];
        spectrumIndexBottom = 0;
//        LogCreate();

        LinearLayout layout2 = (LinearLayout)findViewById(R.id.frequencyspectrum);
        LinearLayout layout1 = (LinearLayout)findViewById(R.id.timeseriesgraph);
//        series[0] = new XYSeries("16k");
//        series[1] = new XYSeries("18k");
//        series[2]= new XYSeries("16k");
//        series[3] = new XYSeries("18k");
        series3 = new XYSeries("spectrumTop");
        series4 = new XYSeries("spectrumBottom");
        mDataset = new XYMultipleSeriesDataset();
        mDataset2 = new XYMultipleSeriesDataset();
        mDataset.addSeries(series[0]);
        mDataset.addSeries(series[1]);
        mDataset.addSeries(series[2]);
        mDataset.addSeries(series[3]);

        mDataset2.addSeries(series3);
        mDataset2.addSeries(series4);

        int color = Color.BLUE;
        PointStyle style = PointStyle.CIRCLE;
        renderer = buildFourRenderer(color, style, true);
        renderer2 = buildTwoRenderer(color, style, true);
        setChartSettings(renderer, "Time", "dBm", 0, 100, -20, 60, Color.WHITE, Color.WHITE);
        setChartSettings(renderer2, "frequency", "dBm", 17500, 18500, -20, 60, Color.WHITE, Color.WHITE);
        chart1 = ChartFactory.getLineChartView(context, mDataset, renderer);
        chart2 = ChartFactory.getLineChartView(context, mDataset2, renderer2);
        layout1.addView(chart1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        layout2.addView(chart2, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));


        if (recordingThread == null) {
            // Init RecBuffer and thread
            mBuffer = new RecBuffer();
            recordingThread = new Thread(mBuffer);
            // register myself to RecBuffer to let it know I'm listening to him
            this.register(mBuffer);
            recordingThread.start();
        }

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                updateChart();
                super.handleMessage(msg);
            }
        };

        task = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                handler.sendMessage(message);
            }
        };

        timer.schedule(task, 500, 50);

        // start a new thread to synthesise audio
        soundPlay = new Thread() {
            public void run() {
                // set process priority
                setPriority(Thread.MAX_PRIORITY);
                // set the buffer size
                int buffsize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                // create an audiotrack object
                AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, buffsize,
                        AudioTrack.MODE_STREAM);

                short samples[] = new short[buffsize];
                int amp = 10000;
                double twopi = 8.*Math.atan(1.);
                double fr = 18000;
                double ph = 0.0;

                // start audio
                audioTrack.play();

                // synthesis loop
                while(isRunning){
                    for(int i=0; i < buffsize; i++){
                        samples[i] = (short) (amp*Math.sin(ph));
                        ph += twopi*fr/AUDIO_SAMPLE_RATE;
                    }
                    audioTrack.write(samples, 0, buffsize);
                }
                audioTrack.stop();
                audioTrack.release();
            }
        };
        soundPlay.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        //Timer
        try {
            soundPlay.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        soundPlay = null;
        recordingThread =null;
        timer.cancel();
        super.onDestroy();
    }

//    private final void receiveAudio(short[] buffer) {
//        // Lock to protect updates to these local variables. See run().
//        synchronized (this) {
//            audioData = buffer;
//            ++audioSequence;
//        }
//    }

//    public void doUpdate() {
//        short[] buffer = null;
//        synchronized (this) {
//            if (audioData != null && audioSequence > audioProcessed) {
//                audioProcessed = audioSequence;
//                buffer = audioData;
//            }
//        }
//
//        // If we got data, process it without the lock.
//        if (buffer != null && buffer.length ==inputBlockSize )
//            processAudio2(buffer);
//
//    }

    int logcounter=0;
    private static DataOutputStream logWriter=null;
    public void LogCreate(){
        if(logWriter == null){
            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            File folder= new File(path+"/SoundLog/");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            logcounter++;
            File f = new File(path+"/SoundLog/Sound"+logcounter+".txt");

            while(f.exists()){
                logcounter++;
                f = new File(path+"/SoundLog/Sound"+logcounter+".txt");
            }

            try {
                f.createNewFile();
                FileOutputStream fos = new FileOutputStream(f);
                logWriter = new DataOutputStream(fos);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    int tick =-1;

    private void  processAudio(short[] buffer) {
        //Process the buffer. While reading it, it needs to be locked.
        short[] tmpBuf = new short[inputBlockSize];
        int[] inputBuf = new int[inputBlockSize];

        synchronized (buffer) {
            final int len = buffer.length;
            System.arraycopy(buffer, 0, tmpBuf, len - inputBlockSize, inputBlockSize);
            // Tell the reader we're done with the buffer.
            buffer.notify();
        }
        magnitude = 0;

        for (int i = 0; i < inputBlockSize; i++) {
            int tmpint = tmpBuf[i];
            inputBuf[i] = tmpint;
        }

        magnitude = goertzelFilter(inputBuf, mfrequency, inputBlockSize);
        magnitude2 = goertzelFilter(inputBuf, mfrequency-20, inputBlockSize);

        for (int i =0;i<100;i++){
            xv3[i]=17500+20*i;
            yv3[i]=goertzelFilter(inputBuf, xv3[i], inputBlockSize);
		}


    }

    /**
     * use this method to register on RecBuffer to let it know who is listening
     *
     * @param r
     */
    @Override
    public void register(RecBuffer r) {
        // set receiving thread to be this class
        r.setReceiver(this);
    }

    /**
     * This method will be called every time the buffer of recording thread is
     * full. It will do audio processing to get features. And also add these features to KNN
     *
     * @param data
     *            : audio data recorded. For stereo data, data at odd indexes
     *            belongs to one channel, data at even indexes belong to another
     * @throws java.io.FileNotFoundException
     */
    @Override
    public void onRecBufFull(short[] data) {// length 4096*2

//		Log.d(LTAG, "inside onRecBuf full");
        /*******************smooth the data******************/
//        SPUtil.smooth(data);

        /**************** put recording to buffer ********************/
        short[][] audioTwoChannelData = seperateChannels(data);

        final int len = audioTwoChannelData[0].length;//4096
        spectrumAnalyserTop.setInput(audioTwoChannelData[0], len - inputBlockSize,
                    inputBlockSize);
        spectrumAnalyserBottom.setInput(audioTwoChannelData[1], len - inputBlockSize,
                inputBlockSize);

        // Do the (expensive) transformation.
        // The transformer has its own state, no need to lock here.

        spectrumAnalyserTop.transform();
        spectrumAnalyserBottom.transform();

        // Get the FFT output.
        if (historyLen <= 1)
            spectrumAnalyserTop.getResults(spectrumDataTop);
        else
            spectrumIndexTop = spectrumAnalyserTop.getResults(spectrumDataTop,
                    spectrumHistTop, spectrumIndexTop);

        historyLen =1;
        if (historyLen <= 1)
            spectrumAnalyserBottom.getResults(spectrumDataBottom);
        else
            spectrumIndexBottom = spectrumAnalyserBottom.getResults(spectrumDataBottom,
                    spectrumHistBottom, spectrumIndexBottom);

        for (int i =0;i<spectrumsize;i++){
            xv3[i]=17500+10*i;
            yv3[i]=55+10*Math.log10(spectrumDataTop[spectrumDataTop.length*(17500+10*i)*2/AUDIO_SAMPLE_RATE2]);
        }

        for (int i =0;i<spectrumsize;i++){
            xv4[i]=17500+10*i;
            yv4[i]=55+10*Math.log10(spectrumDataBottom[spectrumDataBottom.length*(17500+10*i)*2/AUDIO_SAMPLE_RATE2]);
        }

        FindPilotBandWidth(1,xv3, yv3);//top
        FindPilotBandWidth(2,xv4, yv4);//bottom
        addY[0] = 55+10*Math.log10(spectrumDataTop[spectrumDataTop.length*(17900)*2/AUDIO_SAMPLE_RATE2]);
        addY[1] = 70+10*Math.log10(spectrumDataTop[spectrumDataTop.length*(18100)*2/AUDIO_SAMPLE_RATE2]);
        addY[2] = 85+10*Math.log10(spectrumDataBottom[spectrumDataBottom.length*(17900)*2/AUDIO_SAMPLE_RATE2]);
        addY[3] = 100+10*Math.log10(spectrumDataBottom[spectrumDataBottom.length*(18100)*2/AUDIO_SAMPLE_RATE2]);

    }

    private static final int CHUNKSIZE= 4096;
    public static short[][] seperateChannels(short[] dataAll) {
        short[][] twoChannels = new short[2][CHUNKSIZE];
        int i;
        int len = dataAll.length / 2;
        for (i = 0; i < len; i++) {
            twoChannels[0][i] = dataAll[2 * i];
            twoChannels[1][i] = dataAll[2 * i + 1];
        }

        return twoChannels;
    }

//    /**
//     * Handle audio input. This is called on the thread of the parent surface.
//     *
//     * @param buffer
//     *            Audio data that was just read.
//     */
//    private void processAudio2(short[] buffer) {
//
//        short[] tmpBuf = new short[inputBlockSize];
//
//        // Process the buffer. While reading it, it needs to be locked.
//        synchronized (buffer) {
//            // Calculate the power now, while we have the input
//            // buffer; this is pretty cheap.
//            final int len = buffer.length;
//
//            spectrumAnalyser.setInput(buffer, len - inputBlockSize,
//                    inputBlockSize);
//            // Tell the reader we're done with the buffer.
//            buffer.notify();
//        }
//        // Do the (expensive) transformation.
//        // The transformer has its own state, no need to lock here.
//
//        spectrumAnalyser.transform();
//
//        // Get the FFT output.
//        if (historyLen <= 1)
//            spectrumAnalyser.getResults(spectrumData);
//        else
//            spectrumIndex = spectrumAnalyser.getResults(spectrumData,
//                    spectrumHist, spectrumIndex);
//
//		for (int i =0;i<spectrumsize;i++){
//            xv3[i]=17500+10*i;
//            yv3[i]=60+10*Math.log10(spectrumData[spectrumData.length*(17500+10*i)*2/AUDIO_SAMPLE_RATE]);
//		}
//
//        FindPilotBandWidth(xv3, yv3);
//        addY = 60+10*Math.log10(spectrumData[spectrumData.length*(17900)*2/AUDIO_SAMPLE_RATE]);
//        addY2 = 80+10*Math.log10(spectrumData[spectrumData.length*(18100)*2/AUDIO_SAMPLE_RATE]);
//    }

//    int leftBandwidth =0;
//    int rightBandwidth = 0;
    Handler mHandler = new Handler();
    int pushCountTop =0;
    int pullCountTop = 0;
    int pushCountBottom =0;
    int pullCountBottom = 0;
//    int AverageInRightBandwidth = 0;
//    int AverageInLeftBandwidth = 0;
    int eventCounter =0;
    boolean IsPush = false;
    boolean IsPull = false;
    boolean IsPreamble = false;
    boolean lastPreamble = false;
    int lastStateIsPush = -1;
    int PreambleCounter = 0;

    public synchronized void  FindPilotBandWidth(final int index, double[] x, double[] y){
        IsPush = false;
        IsPull = false;
        IsPreamble = false;
        double threshold;
        if(index==1){
            lastStateIsPush=-1;
            eventCounter++;
        }

        if(index ==1){//top
           threshold = 0.3*y[y.length/2];
        }
        else{//bottom
           threshold = 0.2*y[y.length/2];
        }

        int leftBandwidth =0;
        int  rightBandwidth = 0;
        int  AverageInLeftBandwidth=0;
        int  AverageInRightBandwidth=0;


        for(int i = y.length/2+5; i<y.length;i+=1){
            if(y[i]>threshold){
                rightBandwidth+=10;
                AverageInRightBandwidth+=y[i];
            }
            else
                break;
        }
        if(rightBandwidth>20)
            AverageInRightBandwidth/=(rightBandwidth)/10;

        for(int i = y.length/2-5; i>0;i-=1){
            if(y[i]>threshold){
                leftBandwidth+=10;
                AverageInLeftBandwidth+=y[i];
            }
            else
                break;
        }
        if(leftBandwidth>20)
            AverageInLeftBandwidth/=(leftBandwidth)/10;

        final double rightStrength = AverageInRightBandwidth/y[y.length/2];
        final double leftStrength = AverageInLeftBandwidth/y[y.length/2];

        final double [] outputBand= new double[]{(leftBandwidth+50)/100.0,(rightBandwidth+50)/100.0};
        final int tempCounter = eventCounter;
        final int tempCounter2= PreambleCounter;

        if((rightBandwidth -leftBandwidth) >80){// && lastPreamble){
            IsPush = true;
            PreambleCounter++;
            if(PreambleCounter>10)
                lastPreamble =false;
            if(index ==1)
                lastStateIsPush =1;
        }
        else if((leftBandwidth-rightBandwidth)>80 ){//&& lastPreamble){
            IsPull = true;
            PreambleCounter++;
            if(PreambleCounter>10)
                lastPreamble =false;
            if(index ==1)
                lastStateIsPush =0;
        }
        else if(leftBandwidth>100 && leftBandwidth<250 && rightBandwidth >100 && rightBandwidth<250 && !lastPreamble){
            IsPreamble = true;
            lastPreamble =true;
            PreambleCounter=0;
        }

        if(IsPush){
           if(index ==1){
               pushCountTop++;
               pullCountTop=0;

               if(pushCountTop>0){
                    pushCountTop=0;
                    mHandler.post(new Runnable() {
                        public void run() {
                                Textbox.setText("No."+tempCounter+" "+outputBand[0]+" "+outputBand[1]+" push "+round(rightStrength,2)+"\n"+Textbox.getText());
                        }
                    });
               }
           }
           else{
               pushCountBottom++;
               pullCountBottom=0;

               if(pushCountBottom>0){
                    pushCountBottom=0;
                       mHandler.post(new Runnable() {
                           public void run() {
                               Textbox.setText("No."+tempCounter+" "+"---Bottom-----"+outputBand[0]+" "+outputBand[1]+" push "+round(rightStrength,2)+"\n"+Textbox.getText());
                           }
                    });
               }

           }

        }else if(IsPull){
            if(index ==1){
                pushCountTop++;
                pullCountTop=0;

                if(pushCountTop>0){
                    pushCountTop=0;
                    mHandler.post(new Runnable() {
                        public void run() {
                            Textbox.setText("No."+tempCounter+" "+outputBand[0]+" "+outputBand[1]+" pull "+round(leftStrength,2)+"\n"+Textbox.getText());
                        }
                    });
                }
            }
            else{
                pushCountBottom++;
                pullCountBottom=0;

                if(pushCountBottom>0){
                    pushCountBottom=0;
                    mHandler.post(new Runnable() {
                        public void run() {
                            Textbox.setText("No."+tempCounter+" "+"---Bottom-----"+outputBand[0]+" "+outputBand[1]+" pull "+round(leftStrength,2)+"\n"+Textbox.getText());
                        }
                    });
                }

            }

        }
        else if(IsPreamble){
            mHandler.post(new Runnable() {
                public void run() {
                    Textbox.setText("No."+tempCounter+"***Preamble****"+outputBand[0]+" "+outputBand[1]+" "+tempCounter2+" times"+"\n"+Textbox.getText());
                }
            });
        }
        if(index ==2){
            if((IsPush && lastStateIsPush ==0)||(IsPull&&lastStateIsPush==1)){
                mHandler.post(new Runnable() {
                    public void run() {
                        Textbox.setText("(^_^)Difference found"+"\n"+Textbox.getText());
                    }
                });
            }

        }
    }

    public double goertzelFilter(int samples[], double freq, int N) {
        double s_prev = 0.0;
        double s_prev2 = 0.0;
        double coeff,normalizedfreq,power,s;
        int i;
        normalizedfreq = freq / AUDIO_SAMPLE_RATE;
        coeff = 2* Math.cos(2 * Math.PI * normalizedfreq);
        for (i=0; i<N; i++) {
            s = samples[i] + coeff * s_prev - s_prev2;
            s_prev2 = s_prev;
            s_prev = s;
        }
        power = s_prev2*s_prev2+s_prev*s_prev-coeff*s_prev*s_prev2;
        if(power <1){
            power =1;
        }
        return 10*Math.log10(power);
    }

    public  double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    protected XYMultipleSeriesRenderer buildTwoRenderer(int color, PointStyle style, boolean fill) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

        //??
        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(color);
        r.setPointStyle(style);
        r.setFillPoints(fill);
        r.setLineWidth(3);
//        addY2 = magnitude2;
        XYSeriesRenderer r2 = new XYSeriesRenderer();
        r2.setColor(Color.BLACK);
        r2.setPointStyle(style);
        r2.setFillPoints(fill);
        r2.setLineWidth(3);

        renderer.addSeriesRenderer(r);
        renderer.addSeriesRenderer(r2);

        return renderer;
    }


    protected XYMultipleSeriesRenderer buildFourRenderer(int color, PointStyle style, boolean fill) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(Color.GREEN);
        r.setPointStyle(style);
        r.setFillPoints(fill);
        r.setLineWidth(3);

        XYSeriesRenderer r2 = new XYSeriesRenderer();
        r2.setColor(Color.BLACK);
        r2.setPointStyle(style);
        r2.setFillPoints(fill);
        r2.setLineWidth(3);

        XYSeriesRenderer r3 = new XYSeriesRenderer();
        r3.setColor(Color.BLUE);
        r3.setPointStyle(style);
        r3.setFillPoints(fill);
        r3.setLineWidth(3);

        XYSeriesRenderer r4 = new XYSeriesRenderer();
        r4.setColor(Color.RED);
        r4.setPointStyle(style);
        r4.setFillPoints(fill);
        r4.setLineWidth(3);

        renderer.addSeriesRenderer(r);
        renderer.addSeriesRenderer(r2);
        renderer.addSeriesRenderer(r3);
        renderer.addSeriesRenderer(r4);

        return renderer;
    }

    protected XYMultipleSeriesRenderer buildOneRenderer(int color, PointStyle style, boolean fill) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(color);
        r.setPointStyle(style);
        r.setFillPoints(fill);
        r.setLineWidth(3);

        renderer.addSeriesRenderer(r);

        return renderer;
    }

    protected void setChartSettings(XYMultipleSeriesRenderer renderer, String xTitle, String yTitle,
                                    double xMin, double xMax, double yMin, double yMax, int axesColor, int labelsColor) {
        //api
        renderer.setChartTitle(title);
        renderer.setXTitle(xTitle);
        renderer.setYTitle(yTitle);
        renderer.setXAxisMin(xMin);
        renderer.setXAxisMax(xMax);
        renderer.setYAxisMin(yMin);
        renderer.setYAxisMax(yMax);
        renderer.setAxesColor(axesColor);
        renderer.setLabelsColor(labelsColor);
//        renderer.setShowGrid(true);
        renderer.setGridColor(Color.GREEN);
        renderer.setXLabels(40);
        renderer.setYLabels(40);
        renderer.setYLabelsAlign(Paint.Align.RIGHT);
        renderer.setPointSize((float) 2);
        renderer.setShowLegend(false);
        renderer.setChartTitleTextSize(30);
        int[]  margin = new int[]{50,50,50,50};
        renderer.setMargins(margin);
        renderer.setAxisTitleTextSize(30);
        renderer.setLabelsTextSize(20);
    }

    private void updateChart() {
        mDataset2.removeSeries(series3);
        mDataset2.removeSeries(series4);

        int length = series[0].getItemCount();
        if (length > 100) {
            length = 100;
        }

        for(int k=0;k<4;k++){
            addX[k]=0;
            mDataset.removeSeries(series[k]);
            for (int i = 0; i < length; i++) {
                xv[k][i] = (int) series[k].getX(i) + 1;
                yv[k][i] = (int) series[k].getY(i);
            }
            series[k].clear();
            series[k].add(0, addY[k]);
        }
        series3.clear();
        series4.clear();

        for(int i=0;i<4;i++){
            for (int k = 0; k < length; k++) {
                series[i].add(xv[i][k], yv[i][k]);
            }
            mDataset.addSeries(series[i]);
        }

        for (int k = 0; k < spectrumsize; k++) {
            series3.add(xv3[k], yv3[k]);
        }
        for (int k = 0; k < spectrumsize; k++) {
            series4.add(xv4[k], yv4[k]);
        }


        mDataset2.addSeries(series3);
        mDataset2.addSeries(series4);
        chart1.invalidate();
        chart2.invalidate();

    }
}

