package com.codestates.coplit; 

public class Solution { 
	public int computePower(int base, int exponent) {
    int result=1;
    if (exponent == 0){
      return 1;
    }
    for(int i=0; i<exponent; i++){
      result *= base;
    }
    return result;
	} 
}
