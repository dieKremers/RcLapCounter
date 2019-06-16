package com.example.rclapcounter;

public class ResultModel {
        String text;
        int counter;
        double seconds;

        ResultModel(String text, int counter, double seconds) {
            this.text = text;
            this.counter = counter;
            this.seconds = seconds;
        }

        @Override
        public String toString() {
            return text + " " + counter + ":\t" + seconds + "s";
        }

}
