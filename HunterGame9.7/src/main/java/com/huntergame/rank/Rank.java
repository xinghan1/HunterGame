package com.huntergame.rank;

public class Rank {
   private final String name;
   private final double min;
   private final double max;

   public Rank(String name, double min, double max) {
      this.name = name;
      this.min = min;
      this.max = max;
   }

   public String getName() {
      return this.name;
   }

   public double getMin() {
      return this.min;
   }

   public double getMax() {
      return this.max;
   }
}
