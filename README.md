# moka7-live

## How to use

    # args: name, IP, db1, db2, db number plc->pc, db number pc->plc, booleans to listen to changes to
    PLC plc1 = new PLC("Test PLC1","10.10.21.10",new byte[32],new byte[36],112,114,new double[]{0.1,0.2});
    PLC plc2 = new PLC("Test PLC2", "10.10.22.10",new byte[18],new byte[22],45,44,new double[]{0.1,0.2,0.3});
