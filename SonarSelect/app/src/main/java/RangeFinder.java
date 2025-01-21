/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.

This part was transcribed LLAP (https://github.com/Samsonsjarkal/LLAPcode) to JAVA for Android implementation
SonarSelect adjusted hyper-parameter in the LEVD algorithm to focus on nearby movements.
These adjustment can be varied up to device models.
*/
public class RangeFinder {

    // define constants
    public static final int MAX_NUM_FREQS = 16;             //max number of frequency
    public static final double PI = 3.1415926535;           //pi
    public static final int AUDIO_SAMPLE_RATE = 48000;      //should be the same as in controller, will add later
    public static final int TEMPERATURE = 26;               //default temperature
    private static final double VOLUME = 1;               //volume
    private static final int CIC_SEC = 4;                   //cic filter stages
    private static final int CIC_DEC = 16;                  //cic filter decimation
    private static final int CIC_DELAY = 17;                //cic filter delay
    private static final int SOCKETBUFLEN = 40960;          //socket buffer length
    private static final int POWER_THR = 15000;             //power threshold: default 15000
    private static final int PEAK_THR = 800;                //peak threshold: default 220
    private static final double DC_TREND = 0.25;            //dc_trend threshold: default 0.25

    // variables
    public int      mSocBufPos;

    private int         mNumFreqs;                                  //number of frequency
    private int         mCurPlayPos;                                //current play position
    private int         mCurProcPos;                                //current process position
    public int         mCurRecPos;                                 //current receive position
    private int         mLastCICPos;                                //last cic filter position
    private int         mBufferSize;                                //buffer size
    private int         mRecDataSize;                               //receive data size
    private int         mDecsize;                                   //buffer size after decimation
    private double      mFreqInterv;                                //frequency interval
    private double      mSoundSpeed;                                //sound speed
    private double[]    mFreqs = new double[MAX_NUM_FREQS];         //frequency of the ultsound signal
    private double[]    mWaveLength = new double[MAX_NUM_FREQS];    //wave length of the ultsound signal

    private int[]           mPlayBuffer;
    public double[]           mRecDataBuffer;
    private double[]        mFRecDataBuffer;
    private double[]        mTempBuffer;

    private int             mPlayBuffer_p;
    public int              mRecDataBuffer_p;
    private int             mFRecDataBuffer_p;
    private double[][]      mSinBuffer_p;
    private double[][]      mCosBuffer_p;
    private double[][]      mBaseBandReal_p;
    private double[][]      mBaseBandImage_p;
    private int             mTempBuffer_p;
    private double[][][][]  mCICBuffer_p;
    private int[]           mSocketBuffer = new int[SOCKETBUFLEN];
    private double[][]      mDCValue = new double[2][MAX_NUM_FREQS];
    private double[][]      mMaxValue = new double[2][MAX_NUM_FREQS];
    private double[][]      mMinValue = new double[2][MAX_NUM_FREQS];
    private double[]        mFreqPower = new double[MAX_NUM_FREQS];


    public RangeFinder(int inMaxFramesPerSlice , int inNumFreq, double inStartFreq, double inFreqInterv){

        mNumFreqs = inNumFreq;                      //Number of frequency
        mBufferSize = inMaxFramesPerSlice;          //Buffer size
        mFreqInterv = inFreqInterv;                 //Frequency interval
        mRecDataSize = inMaxFramesPerSlice*4;
        mSoundSpeed = 331.3 + 0.606 * TEMPERATURE;  //Sound speed

        //Init buffer
        mSinBuffer_p = new double[MAX_NUM_FREQS][2*inMaxFramesPerSlice];
        mCosBuffer_p = new double[MAX_NUM_FREQS][2*inMaxFramesPerSlice];
        mBaseBandReal_p = new double[MAX_NUM_FREQS][mRecDataSize/CIC_DEC];
        mBaseBandImage_p = new double[MAX_NUM_FREQS][mRecDataSize/CIC_DEC];
        mCICBuffer_p = new double[MAX_NUM_FREQS][CIC_SEC][2][mRecDataSize/CIC_DEC+CIC_DELAY];

        for(int i=0; i<MAX_NUM_FREQS; i++){
            mFreqs[i]=inStartFreq+i*inFreqInterv;
            mWaveLength[i]=mSoundSpeed/mFreqs[i]*1000; //all distance is in mm
        }

        mPlayBuffer = new int[2*inMaxFramesPerSlice];
        mRecDataBuffer = new double[mRecDataSize];
        mFRecDataBuffer = new double[mRecDataSize];
        mTempBuffer = new double[mRecDataSize];
        mPlayBuffer_p = 0;
        mRecDataBuffer_p = 0;
        mFRecDataBuffer_p = 0;
        mTempBuffer_p = 0;
        mCurPlayPos = 0;
        mCurRecPos = 0;
        mCurProcPos= 0;
        mLastCICPos =0;
        mDecsize=0;
        mSocBufPos=0;

        initBuffer();
    }

    void initBuffer(){
        for(int i=0; i<mNumFreqs; i++){
            for(int n=0; n<mBufferSize*2; n++){
                mCosBuffer_p[i][n]= Math.cos(2*PI*n/AUDIO_SAMPLE_RATE*mFreqs[i]);
                mSinBuffer_p[i][n]=-Math.sin(2*PI*n/AUDIO_SAMPLE_RATE*mFreqs[i]);
            }
            mDCValue[0][i]=0;
            mMaxValue[0][i]=0;
            mMinValue[0][i]=0;
            mDCValue[1][i]=0;
            mMaxValue[1][i]=0;
            mMinValue[1][i]=0;
        }

        float mTempSample;

        for(int n=0; n<mBufferSize*2; n++){
            mTempSample=0;
            for(int i=0; i<mNumFreqs; i++){
                mTempSample+=mCosBuffer_p[i][n]*VOLUME;
            }
            mPlayBuffer[n]=(int) (mTempSample/mNumFreqs*32767);
        }
    }

    int GetRecDataBuffer(int inSamples){
        int RecDataPointer = mRecDataBuffer_p + mCurRecPos;
        mCurRecPos += inSamples;

        //over flowed RecBuffer
        if(mCurRecPos >= mRecDataSize) {
            mCurRecPos = 0;
            RecDataPointer = mRecDataBuffer_p;
        }
        return RecDataPointer;
    }

    double GetDistanceChange(){
        double distancechange=0;
        //each time we process the data in the RecDataBuffer and clear the mCurRecPos

        //Get base band signal
        GetBaseBand();

        //Remove dcvalue from the baseband signal
        RemoveDC();

        //Calculate distance from the phase change
        distancechange = CalculateDistance();

        return distancechange;
    }

    double CalculateDistance() {
        double distance = 0;
        ComplexDT[] tempcomplex;
        double[] tempdata = new double[4096];
        double[] tempdata2 = new double[4096];
        double[] tempdata3 = new double[4096];
        double temp_val;
        double[][] phasedata = new double[MAX_NUM_FREQS][4096];
        int[] ignorefreq = new int[MAX_NUM_FREQS];

        if (mDecsize > 4096)
            return 0;

        for(int f=0;f<mNumFreqs;f++)
        {
            ignorefreq[f]=0;

            //get complex number
            tempcomplex = new ComplexDT[mRecDataSize/CIC_DEC];
            for (int n=0; n<tempcomplex.length; n++) {
                tempcomplex[n] = new ComplexDT(mBaseBandReal_p[f][n], mBaseBandImage_p[f][n]);
            }

            //get magnitude
            tempdata = vDSP_zvmags(tempcomplex, tempdata, mDecsize);
            temp_val = vDSP_sve(tempdata, mDecsize);

            if(temp_val/mDecsize>POWER_THR) //only calculate the high power vectors
            {
                phasedata[f] = vDSP_zvphas(tempcomplex, phasedata[f], mDecsize);
                //phase unwarp
                for(int i=1;i<mDecsize;i++)
                {
                    while(phasedata[f][i]-phasedata[f][i-1]>PI)
                        phasedata[f][i]=phasedata[f][i]-2*PI;
                    while(phasedata[f][i]-phasedata[f][i-1]<-PI)
                        phasedata[f][i]=phasedata[f][i]+2*PI;
                }
                if(Math.abs(phasedata[f][mDecsize-1]-phasedata[f][0])>PI/4)
                {
                    for(int i=0;i<=1;i++)
                        mDCValue[i][f]=(1-DC_TREND*2)*mDCValue[i][f]+
                                (mMinValue[i][f]+mMaxValue[i][f])/2*DC_TREND*2;
                }

                temp_val=-phasedata[f][0];
                tempdata = vDSP_vsadd(phasedata[f], temp_val, tempdata, mDecsize);
                temp_val=2*PI/mWaveLength[f];
                phasedata[f] = vDSP_vsdiv(tempdata, temp_val, phasedata[f], mDecsize);
            }
            else //ignore the low power vectors
            {
                ignorefreq[f]=1;
            }

        }

        //linear regression
        for(int i=0;i<mDecsize;i++)
            tempdata2[i]=i;
        double sumxy=0;
        double sumy=0;
        int     numfreqused=0;
        for(int f=0;f<mNumFreqs;f++)
        {
            if(ignorefreq[f] != 0)
            {
                continue;
            }

            numfreqused++;

            tempdata = vDSP_vmul(phasedata[f], tempdata2, tempdata, mDecsize);
            temp_val = vDSP_sve(tempdata, mDecsize);
            sumxy+=temp_val;
            temp_val = vDSP_sve(phasedata[f], mDecsize);
            sumy+=temp_val;

        }
        if(numfreqused==0)
        {
            distance=0;
            return distance;
        }

        double deltax=mNumFreqs*((mDecsize-1)*mDecsize*(2*mDecsize-1)/6-(mDecsize-1)*mDecsize*(mDecsize-1)/4);
        double delta=(sumxy-sumy*(mDecsize-1)/2.0)/deltax*mNumFreqs/numfreqused;

        double varsum=0;
        double[] var_val = new double[MAX_NUM_FREQS];
        for(int i=0;i<mDecsize;i++)
            tempdata2[i]=i*delta;

        for(int f=0;f<mNumFreqs;f++)
        {   var_val[f]=0;
            if(ignorefreq[f] != 0)
            {
                continue;
            }
            tempdata = vDSP_vsub(tempdata2, phasedata[f], tempdata, mDecsize);
            tempdata3 = vDSP_vsq(tempdata, tempdata3, mDecsize);
            var_val[f] = vDSP_sve(tempdata3, mDecsize);
            varsum+=var_val[f];
        }
        varsum=varsum/numfreqused;
        for(int f=0;f<mNumFreqs;f++)
        {
            if(ignorefreq[f] != 0)
            {
                continue;
            }
            if(var_val[f]>varsum)
                ignorefreq[f]=1;
        }

        //linear regression
        for(int i=0;i<mDecsize;i++)
            tempdata2[i]=i;

        sumxy=0;
        sumy=0;
        numfreqused=0;
        for(int f=0;f<mNumFreqs;f++)
        {
            if(ignorefreq[f] != 0)
            {
                continue;
            }

            numfreqused++;

            tempdata = vDSP_vmul(phasedata[f],tempdata2,tempdata,mDecsize);
            temp_val = vDSP_sve(tempdata, mDecsize);
            sumxy+=temp_val;
            temp_val = vDSP_sve(phasedata[f], mDecsize);
            sumy+=temp_val;

        }
        if(numfreqused==0)
        {
            distance=0;
            return distance;
        }

        delta=(sumxy-sumy*(mDecsize-1)/2.0)/deltax*mNumFreqs/numfreqused;

        distance = -delta*mDecsize/2;
        return distance;
    }

    void RemoveDC(){
        int f,i;
        double[] tempdata = new double[4096];
        double[] tempdata2 = new double[4096];
        double temp_val;
        double vsum,dsum,max_valr,min_valr,max_vali,min_vali;
        if(mDecsize>4096){
            return;
        }

        //'Levd' algorithm to calculate the DC value;
        for(f=0;f<mNumFreqs;f++)
        {
            vsum=0;
            dsum=0;
            //real part
            max_valr = vDSP_maxv(mBaseBandReal_p[f], mDecsize);
            min_valr = vDSP_minv(mBaseBandReal_p[f], mDecsize);
            //getvariance,first remove the first value
            temp_val=-mBaseBandReal_p[f][0];
            tempdata = vDSP_vsadd(mBaseBandReal_p[f],temp_val,tempdata,mDecsize);
            temp_val = vDSP_sve(tempdata, mDecsize);
            dsum=dsum+Math.abs(temp_val)/mDecsize;
            tempdata2 = vDSP_vsq(tempdata, tempdata2,mDecsize);
            temp_val = vDSP_sve(tempdata2,mDecsize);
            vsum=vsum+Math.abs(temp_val)/mDecsize;

            //imag part
            max_vali = vDSP_maxv(mBaseBandImage_p[f], mDecsize);
            min_vali = vDSP_minv(mBaseBandImage_p[f], mDecsize);
            //getvariance,first remove the first value
            temp_val = -mBaseBandImage_p[f][0];
            tempdata = vDSP_vsadd(mBaseBandImage_p[f], temp_val,tempdata,mDecsize);
            temp_val = vDSP_sve(tempdata, mDecsize);
            dsum=dsum + Math.abs(temp_val)/mDecsize;
            tempdata2 = vDSP_vsq(tempdata,tempdata2,mDecsize);
            temp_val = vDSP_sve(tempdata2,mDecsize);
            vsum=vsum + Math.abs(temp_val)/mDecsize;

            mFreqPower[f]=(vsum+dsum*dsum);///fabs(vsum-dsum*dsum)*vsum;

            //Get DC estimation
            if(mFreqPower[f]>POWER_THR)
            {
                if ( max_valr > mMaxValue[0][f] ||
                        (max_valr > mMinValue[0][f]+PEAK_THR &&
                                (mMaxValue[0][f]-mMinValue[0][f]) > PEAK_THR*4) )
                {
                    mMaxValue[0][f]=max_valr;
                }

                if ( min_valr < mMinValue[0][f] ||
                        (min_valr < mMaxValue[0][f]-PEAK_THR &&
                                (mMaxValue[0][f]-mMinValue[0][f]) > PEAK_THR*4) )
                {
                    mMinValue[0][f]=min_valr;
                }

                if ( max_vali > mMaxValue[1][f] ||
                        (max_vali > mMinValue[1][f]+PEAK_THR &&
                                (mMaxValue[1][f]-mMinValue[1][f]) > PEAK_THR*4) )
                {
                    mMaxValue[1][f]=max_vali;
                }

                if ( min_vali < mMinValue[1][f] ||
                        (min_vali < mMaxValue[1][f]-PEAK_THR &&
                                (mMaxValue[1][f]-mMinValue[1][f]) > PEAK_THR*4) )
                {
                    mMinValue[1][f]=min_vali;
                }


                if ( (mMaxValue[0][f]-mMinValue[0][f]) > PEAK_THR &&
                        (mMaxValue[1][f]-mMinValue[1][f]) > PEAK_THR )
                {
                    for(i=0;i<=1;i++)
                        mDCValue[i][f]=(1-DC_TREND)*mDCValue[i][f]+
                                (mMinValue[i][f]+mMaxValue[i][f])/2*DC_TREND;
                }

            }

            //remove DC
            for(i=0;i<mDecsize;i++)
            {
                mBaseBandReal_p[f][i]=mBaseBandReal_p[f][i]-mDCValue[0][f];
                mBaseBandImage_p[f][i]=mBaseBandImage_p[f][i]-mDCValue[1][f];
            }

        }
    }

    void GetBaseBand(){
        int i,index,decsize,cid;
        decsize=mCurRecPos/CIC_DEC;
        mDecsize=decsize;

        //change data from int to float32
        for(i=0;i<mCurRecPos; i++) {
            mFRecDataBuffer[i]= (double) (mRecDataBuffer[i]/32767.0);
        }

        for(i=0;i<mNumFreqs; i++)//mNumFreqs
        {

            //vDSP_vmul(mFRecDataBuffer,1,mCosBuffer[i]+mCurProcPos,1,mTempBuffer,1,mCurRecPos);
            for (int n=0; n<mCurRecPos; n++){
                mTempBuffer[n] = mFRecDataBuffer[n] * mCosBuffer_p[i][n+mCurProcPos];
            }

            cid=0;

            //sum CIC_DEC points of data, put into CICbuffer
            mCICBuffer_p[i][0][cid] = memmove(mCICBuffer_p[i][0][cid], mCICBuffer_p[i][0][cid], mLastCICPos, CIC_DELAY);
            index=CIC_DELAY;
            for(int k=0;k<mCurRecPos;k+=CIC_DEC)
            {
                mCICBuffer_p[i][0][cid][index] = 0;
                for (int n=0; n<CIC_DEC; n++){
                    mCICBuffer_p[i][0][cid][index] += mTempBuffer[k+n];
                }
                index++;
            }

            //prepare CIC first level
            mCICBuffer_p[i][1][cid] = memmove(mCICBuffer_p[i][1][cid], mCICBuffer_p[i][1][cid], mLastCICPos, CIC_DELAY);
            //Sliding window sum
            mCICBuffer_p[i][1][cid] = vDSP_vswsum(mCICBuffer_p[i][0][cid], mCICBuffer_p[i][1][cid], CIC_DELAY, decsize, CIC_DELAY);
            //prepare CIC second level
            mCICBuffer_p[i][2][cid] = memmove(mCICBuffer_p[i][2][cid], mCICBuffer_p[i][2][cid], mLastCICPos, CIC_DELAY);
            //Sliding window sum
            mCICBuffer_p[i][2][cid] = vDSP_vswsum(mCICBuffer_p[i][1][cid],mCICBuffer_p[i][2][cid], CIC_DELAY, decsize, CIC_DELAY);
            //prepare CIC third level
            mCICBuffer_p[i][3][cid] = memmove(mCICBuffer_p[i][3][cid],mCICBuffer_p[i][3][cid], mLastCICPos,CIC_DELAY);
            //Sliding window sum
            mCICBuffer_p[i][3][cid] = vDSP_vswsum(mCICBuffer_p[i][2][cid],mCICBuffer_p[i][3][cid], CIC_DELAY, decsize, CIC_DELAY);
            //CIC last level to Baseband
            mBaseBandReal_p[i] = vDSP_vswsum(mCICBuffer_p[i][3][cid],mBaseBandReal_p[i],0, decsize, CIC_DELAY);


            //multiply the sin
            for (int n=0; n<mCurRecPos; n++){
                mTempBuffer[n] = mFRecDataBuffer[n] * mSinBuffer_p[i][n+mCurProcPos];
            }

            cid=1;

            //sum CIC_DEC points of data, put into CICbuffer
            mCICBuffer_p[i][0][cid] = memmove(mCICBuffer_p[i][0][cid],mCICBuffer_p[i][0][cid], mLastCICPos,CIC_DELAY);
            index=CIC_DELAY;
            for(int k=0;k<mCurRecPos;k+=CIC_DEC)
            {
                mCICBuffer_p[i][0][cid][index] = 0;
                for (int n=0; n<CIC_DEC; n++){
                    mCICBuffer_p[i][0][cid][index] += mTempBuffer[k+n];
                }
                index++;
            }

            //prepare CIC first level
            mCICBuffer_p[i][1][cid] = memmove(mCICBuffer_p[i][1][cid], mCICBuffer_p[i][1][cid], mLastCICPos, CIC_DELAY);
            //Sliding window sum
            mCICBuffer_p[i][1][cid] = vDSP_vswsum(mCICBuffer_p[i][0][cid], mCICBuffer_p[i][1][cid], CIC_DELAY, decsize, CIC_DELAY);
            //prepare CIC second level
            mCICBuffer_p[i][2][cid] = memmove(mCICBuffer_p[i][2][cid], mCICBuffer_p[i][2][cid], mLastCICPos, CIC_DELAY);
            //Sliding window sum
            mCICBuffer_p[i][2][cid] = vDSP_vswsum(mCICBuffer_p[i][1][cid],mCICBuffer_p[i][2][cid], CIC_DELAY, decsize, CIC_DELAY);
            //prepare CIC third level
            mCICBuffer_p[i][3][cid] = memmove(mCICBuffer_p[i][3][cid],mCICBuffer_p[i][3][cid], mLastCICPos,CIC_DELAY);
            //Sliding window sum
            mCICBuffer_p[i][3][cid] = vDSP_vswsum(mCICBuffer_p[i][2][cid],mCICBuffer_p[i][3][cid], CIC_DELAY, decsize, CIC_DELAY);
            //CIC last level to Baseband
            mBaseBandImage_p[i] = vDSP_vswsum(mCICBuffer_p[i][3][cid],mBaseBandImage_p[i],0, decsize, CIC_DELAY);

        }

        mCurProcPos=mCurProcPos+mCurRecPos;
        if(mCurProcPos >= mBufferSize)
            mCurProcPos= mCurProcPos - mBufferSize;
        mLastCICPos=decsize;
        mCurRecPos=0;
    }

    double[] memmove(double[] buffer1, double[] buffer2, int pos, int size){
        for (int i=0; i<size; i++){
            buffer1[i] = buffer2[i+pos];
        }
        return buffer1;
    }

    double[] vDSP_vmul(double[] phasedata, double[] tempdata2, double[] tempdata, int size){
        for(int i=0; i<size; i++) {
            tempdata[i] = phasedata[i] * tempdata2[i];
        }
        return tempdata;
    }

    double vDSP_sve(double[] tempdata, int size){
        double temp_val = 0;
        for(int i=0; i<size; i++) {
            temp_val += tempdata[i];
        }
        return temp_val;
    }

    double vDSP_maxv(double[] input_array, int size){
        double max_val = input_array[0];
        for (int i=0; i<size; i++){
            if (max_val < input_array[i]){
                max_val = input_array[i];
            }
        }
        return max_val;
    }

    double vDSP_minv(double[] input_array, int size){
        double min_val = input_array[0];
        for (int i=0; i<size; i++){
            if (min_val > input_array[i]){
                min_val = input_array[i];
            }
        }
        return min_val;
    }

    double[] vDSP_vsadd(double[] phasedata, double temp_val, double[] tempdata, int size){
        for(int i=0; i<size; i++) {
            tempdata[i] = phasedata[i] + temp_val;
        }
        return tempdata;
    }

    double[] vDSP_vsdiv(double[] tempdata, double temp_val, double[] phasedata, int size){
        for(int i=0; i<size; i++) {
            phasedata[i] = tempdata[i] / temp_val;
        }
        return phasedata;
    }

    double[] vDSP_vswsum(double[] buffer1, double[] buffer2, int pos, int size, int length){
        for(int i=0; i<size; i++) {
            buffer2[i+pos] = 0;
            for (int j=0; j<length; j++){
                buffer2[i+pos] += buffer1[i+j];
            }
        }
        return buffer2;
    }

    //vDSP_vsq(tempdata,1,tempdata3,1,mDecsize);
    double[] vDSP_vsq(double[] tempdata, double[] tempdata3, int size){
        for(int i=0; i<size; i++) {
            tempdata3[i] = Math.pow(tempdata[i],2);
        }
        return tempdata3;
    }

    double[] vDSP_vsub(double[] tempdata2, double[] phasedata, double[] tempdata, int size){
        for(int i=0; i<size; i++){
            tempdata[i] = tempdata2[i] - phasedata[i];
        }
        return tempdata;
    }

    double[] vDSP_zvmags(ComplexDT[] tempcomplex, double[] tempdata, int size){
        for(int i=0; i<size; i++){
            tempdata[i] = Math.pow(tempcomplex[i].re(),2) + Math.pow(tempcomplex[i].im(),2);
        }
        return tempdata;
    }

    double[] vDSP_zvphas(ComplexDT[] tempcomplex, double[] phasedata, int size){
        for(int i=0; i<size; i++){
            phasedata[i] = Math.atan2(tempcomplex[i].im(), tempcomplex[i].re());
        }
        return phasedata;
    }
}
