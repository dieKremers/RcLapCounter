package com.example.rclapcounter;

public class ResultModel {
        String text;
        int counter;
        double seconds;

        ResultModel(){
            text = "N/A";
            counter = -1;
            seconds = -1.0;
        }

        ResultModel(String text, int counter, double seconds) {
            this.text = text;
            this.counter = counter;
            this.seconds = seconds;
        }

        @Override
        public String toString() {
            return String.format("%s %2d:\t%.2f", text, counter, seconds);
            //return text + " " + counter + ":\t" + seconds + "s";
        }

}
