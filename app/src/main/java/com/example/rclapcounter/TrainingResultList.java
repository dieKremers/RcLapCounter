package com.example.rclapcounter;

import java.util.ArrayList;

import javax.xml.transform.Result;

public class TrainingResultList
{
    private int lapCounter=-1;
    private long lastTimestamp = 0;
    private ArrayList<ResultModel> resultList = new ArrayList<>();

    public void notifyCarPassedSensor( long timestamp) {
        if (lapCounter >= 0) //First Time is not a Lap but the initial Start-Time
        {
            double time_s = (timestamp - lastTimestamp) / 1000.0;
            addNewLap("Lap", time_s);
        }
        lastTimestamp = timestamp;
        lapCounter++;
    }

    public int getNumberOfLaps(){
        return lapCounter;
    }
    private void addNewLap(String text, double time){
        resultList.add(new ResultModel(text, lapCounter, time));
    }

    public ArrayList<ResultModel> getResults(){
        return resultList;
    }

    public void clear(){
        resultList.clear();
        lapCounter=-1;
        lastTimestamp=0;
    }

    private ResultModel getFastestLap(){
        double fastestTime = 0.0;
        ResultModel fastestLap = new ResultModel();
        for( ResultModel result : resultList ){
            if( fastestTime == 0 || result.seconds < fastestTime ){
                fastestLap = result;
                fastestTime = result.seconds;
            }
        }
        return fastestLap;
    }

    public double getFastestLapTime_sec(){
        return getFastestLap().seconds;
    }

    public int getFastetLapNumber(){
        return getFastestLap().counter;
    }
}
