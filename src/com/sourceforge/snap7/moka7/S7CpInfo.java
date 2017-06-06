/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sourceforge.snap7.moka7;

/**
*
* @author Dave Nardella
*/
public class S7CpInfo {

    public int MaxPduLength;
    public int MaxConnections;
    public int MaxMpiRate;
    public int MaxBusRate;

    protected void Update(byte[] Src, int Pos)
    {
        MaxPduLength = S7.GetShortAt(Src, 2);
        MaxConnections = S7.GetShortAt(Src, 4);
        MaxMpiRate = S7.GetDIntAt(Src, 6);
        MaxBusRate = S7.GetDIntAt(Src, 10);                      
    }         
}
