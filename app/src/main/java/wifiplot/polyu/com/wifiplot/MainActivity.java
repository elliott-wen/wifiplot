package wifiplot.polyu.com.wifiplot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import org.apache.commons.io.IOUtils;
import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.GZIPInputStream;



public class MainActivity extends Activity {

    class Record
    {
        public
        String address;
        public double timestamp;
        public int rssi;
    };
    List<Record> record_pool = new ArrayList<Record>();
    private XYPlot plot;
    int formatCount=0;
    private double leadingTimestamp = 0;
    DatagramSocket client_socket = null;
    InetAddress serverAddress =  null;
    Timer timer = new Timer(true);
    ConnectivityManager connMgr;
    byte [] triggerData = new byte[1024];
    byte[] receiveData = new byte[1300];

    private List<XYSeries> seriesCollection = new ArrayList<XYSeries>();
    public static final String REMOTESTRING = "192.168.10.1";
    PrintWriter logger = null;
    private Handler uiHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            if(msg.arg1 == -1)
            {
                Toast.makeText(MainActivity.this,"Network Failed",Toast.LENGTH_SHORT).show();
                timer.schedule(new timerTask(), 4000);
            }
            else if (msg.arg1 == -2)
            {
                Toast.makeText(MainActivity.this,"Download OR Parse Failed",Toast.LENGTH_LONG).show();
                timer.schedule(new timerTask(), 1000);
            }
            else if (msg.arg1 == 1)
            {
//                System.out.println(seriesCollection.size());
//                for(int i = 0; i<seriesCollection.size();i++)
//                {
//                    System.out.print("Print out data "+i+" :");
//                    for(int j=0;j<seriesCollection.get(i).size();j++)
//                    {
//                        System.out.print(" "+seriesCollection.get(i).getX(j)+"---"+seriesCollection.get(i).getY(j)+" ");
//                    }
//                    System.out.println(" ");
//                }
                plot.redraw();
                timer.schedule(new timerTask(), 500);
            }
        }

    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        plot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
        plot.setRangeBoundaries(-100, -20, BoundaryMode.FIXED);
        plot.setDrawDomainOriginEnabled(false);
        //plot.setDomainStep(XYStepMode.INCREMENT_BY_PIXELS,1500);
        //plot.getLayoutManager().remove(plot.getDomainLabelWidget());
        plot.getGraphWidget().getDomainLabelPaint().setColor(Color.TRANSPARENT);
        plot.getLayoutManager().remove(plot.getLegendWidget());

        if(!createLogger())
        {
            Toast.makeText(this,"Unable to create logger",Toast.LENGTH_LONG).show();
            return;
        }
        timer.schedule(new timerTask(), 500);
    }

    boolean createLogger()
    {
        try
        {
        String pathName="/sdcard/rssi/";
        String fileName="file.txt";
        File path = new File(pathName);
        File file = new File(pathName + fileName);
        if( !path.exists()) {
            path.mkdir();
        }
        if( !file.exists()) {

            file.createNewFile();
        }
        this.logger = new PrintWriter(new FileOutputStream(file));

        }
        catch(Exception e) {

            e.printStackTrace();
            return false;
        }
        return true;
    }

    public class timerTask extends TimerTask
    {
        public void run()
        {
            if(connMgr==null)
            {
                connMgr = (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
            }
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                try
                {

                    String result = downloadUrl();
                    parseText(result);

                    Message msg = new Message();
                    msg.arg1 = 1;
                    uiHandler.sendMessage(msg);
                    return;
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    Message msg = new Message();
                    msg.arg1 = -2;
                    uiHandler.sendMessage(msg);
                    return;
                }
            } else {

                Message msg = new Message();
                msg.arg1 = -1;
                uiHandler.sendMessage(msg);
            }
        }
    };

    private String downloadUrl() throws Exception {

            if (this.serverAddress == null) {
                this.serverAddress = InetAddress.getByName(REMOTESTRING);
            }
            if(this.client_socket==null)
            {
                this.client_socket = new DatagramSocket();
                client_socket.setSoTimeout(1000);
            }
            DatagramPacket send_packet = new DatagramPacket(triggerData,1, this.serverAddress, 82);
            client_socket.send(send_packet);
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            client_socket.receive(receivePacket);
            String contentAsString = uncompressString(receivePacket.getData());
            return contentAsString;


    }


    public static String uncompressString(byte[] data) throws IOException {
        String result = null;
        GZIPInputStream zi = null;
        try {
            zi = new GZIPInputStream(new ByteArrayInputStream(data));
            result = IOUtils.toString(zi);
        } finally {
            IOUtils.closeQuietly(zi);
        }
        return result;
    }



    public void parseText(String text) throws Exception
    {

        String []res = text.split("\n");
        if((res.length)%3!=0)
        {

            throw new Exception("Unable to parse Text");
        }
        for(int i = 0;i<(res.length)/3;i++)
        {
            Record rec =new Record();
            rec.address = res[i*3];
            rec.timestamp = Integer.valueOf(res[i*3+1]);

            rec.rssi = Integer.valueOf(res[i*3+2]);
            //System.out.println("Record "+rec.address+ " "+rec.timestamp+ " "+rec.rssi);
            boolean find = false;
            for(int j=0;j<record_pool.size();j++)
            {
                if(record_pool.get(j).address.equals(rec.address)) {
                    //System.out.println("Find a record whose mac is "+rec.address);
                    find = true;
                    if(record_pool.get(j).timestamp != rec.timestamp)
                    {
                        //System.out.println("Update rec");
                        record_pool.remove(j);
                        record_pool.add(rec);
                        updateSeries(rec.address,rec.timestamp,rec.rssi);
                    }
                }
            }
            if(!find)
            {
                record_pool.add(rec);
                updateSeries(rec.address,rec.timestamp,rec.rssi);
            }
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void updateSeries(String macaddress, double timestamp, int rssi)
    {
        XYSeries series = null;
        boolean findSeries = false;
        for(int i = 0; i < this.seriesCollection.size(); i ++)
        {

            series = this.seriesCollection.get(i);
            //System.out.println("trying to match oooo "+macaddress +" with "+series.getTitle());
            if(macaddress.equals(series.getTitle()))
            {
                //System.out.println("Match");
                findSeries = true;
                break;
            }
//            if(series.size()>0 && leadingTimestamp != 0)
//            {
//                SimpleXYSeries s = ((SimpleXYSeries)(series));
//
//            }

        }

        if(findSeries)
        {
            SimpleXYSeries s = ((SimpleXYSeries)(series));
            s.addLast(timestamp,rssi);
            logger.write(macaddress+" "+timestamp+" "+rssi+"\n");

            if(timestamp>leadingTimestamp)
            {
                leadingTimestamp = timestamp;
            }

                //((SimpleXYSeries)(series)).addLast(timestamp,rssi);
        }
        else
        {
            series = new SimpleXYSeries(macaddress);
            ((SimpleXYSeries)(series)).addLast(timestamp, rssi);
            this.seriesCollection.add(series);
            plot.addSeries(series, getNextFormater());
            //System.out.println("Add series");
        }
        for(int d=0;d<seriesCollection.size();d++)
        {
            SimpleXYSeries s = (SimpleXYSeries)seriesCollection.get(d);
            if(s.size()>0) {
                double tmp = s.getX(0).doubleValue();
                if (leadingTimestamp - tmp > 15000)//Show 30 second
                {
                    s.removeFirst();
                }
            }
        }
    }

//        @Override public void onStop()
//        {
//            finish();
//            super.onStop();
//        }

    public LineAndPointFormatter getNextFormater()
    {
        this.formatCount ++;
        int color1=0;


        switch(formatCount)
        {
            case 1:
                color1 = Color.WHITE;
                break;
            case 2:
                color1 = Color.BLACK;
                break;
            case 3:
                color1 = Color.GREEN;
                break;

            case 4:
                color1 = Color.LTGRAY;
                break;
            case 5:
                color1 = Color.BLUE;
                break;
            case 6:
                color1 = Color.DKGRAY;
                break;

            case 7:
                color1 = Color.YELLOW;
                break;
            case 8:
                color1 = Color.CYAN;
                break;
            case 9:
                color1 = Color.MAGENTA;
                break;


            default:
                formatCount = 1;
                color1 = Color.WHITE;
                break;
        }
        LineAndPointFormatter formatter2 =
                new LineAndPointFormatter(color1, Color.WHITE, null, null);
        return formatter2;
    }

}
