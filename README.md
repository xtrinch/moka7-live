# moka7-live

## How to use

1. Create classes that implement interface PLCListener

2. Create classes for every PLC you wish to receive bit changes / read integers, bits from

``` 
/*
    args: 
        ** name of PLC
        ** IP of PLC
        ** byte array with length of db PLC->PC
        ** byte array with length of PC->PLC
        ** db (DataBase) number PLC->PC
        ** db (DataBase) number PC->PLC
        ** array of addresses of booleans to listen to changes to
*/
PLC plc1 = new PLC("Test PLC1","10.10.21.10",new byte[32],new byte[36],112,114,new double[]{0.1,0.2});
PLC plc2 = new PLC("Test PLC2", "10.10.22.10",new byte[18],new byte[22],45,44,new double[]{0.1,0.2,0.3}); 
```

3. Add classes that implement interface PLCListener to PLC's `ArrayList<PLCListener> listener` array

```
PLCListenerImplementation myListener = new PLCListenerImplementation();
plc1.listeners.add(myListener)
plc2.listeners.add(myListener)
```

4. Receive bit changes from bits at addresses from last argument of PLC constructor

```
public class PLCListenerImplementation implements PLCListener {
    @Override
    public void PLCBitChanged(int address, int pos, boolean val, String plcName) {
        switch (address) {
        case 0:
            switch (pos) {
            case 1:
                System.out.println("Bit at address 0.1 of PLC " + plcName + " changed to: " + val);
            }
        }
    }
}
```
