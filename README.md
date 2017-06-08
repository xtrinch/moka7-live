# moka7-live

## How to use

**1. Create classes that implement interface PLCListener**

**2. Create PLC class instances for every PLC you wish to receive bit changes / read integers, bits from**

``` 
import si.trina.moka7.live.PLC;

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

**3. Add classes that implement interface PLCListener to PLC's `ArrayList<PLCListener> listener` array**

```
PLCListenerImplementation myListener = new PLCListenerImplementation();
plc1.listeners.add(myListener);
plc2.listeners.add(myListener);
```

**4. Receive bit changes from bits at addresses from last argument of PLC constructor**

```
import si.trina.moka7.live.PLCListener;

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

**5. Write shorts/integers to DB**

```
/*
    args: 
        ** database to write to: from plc = true, from pc = false
        ** address to write to
        ** short/integer to write to db
*/
plc1.putInt(false, 12, (short)3);
plc1.putDInt(false, 12, 3);
```

**6. Read shorts/integers from DB**
```
try {
    short aShort = plc1.getInt(true, 8); // 2 bytes
    int anInteger = plc1.getDInt(true, 8); // 4 bytes
} catch (Exception e) { 
    e.printStackTrace(); 
}
```

## Setup

Package can be installed via maven by adding the following to your pom.xml:

    <dependency>
        <groupId>si.trina</groupId>
        <artifactId>moka7-live</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
