/* Copyright 2012 Yaqiang Wang,
 * yaqiang.wang@gmail.com
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 */
package org.meteoinfo.data.meteodata.hysplit;

import org.meteoinfo.common.DataConvert;
import org.meteoinfo.common.PointD;
import org.meteoinfo.common.util.JDateUtil;
import org.meteoinfo.data.meteodata.*;
import org.meteoinfo.ndarray.Dimension;
import org.meteoinfo.ndarray.DimensionType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.meteoinfo.data.XYListDataset;
import org.meteoinfo.table.ColumnData;
import org.meteoinfo.table.DataColumn;
import org.meteoinfo.table.DataTable;
import org.meteoinfo.ndarray.Array;
import org.meteoinfo.ndarray.DataType;
import org.meteoinfo.ndarray.Index;
import org.meteoinfo.ndarray.InvalidRangeException;
import org.meteoinfo.ndarray.Range;
import org.meteoinfo.ndarray.Section;

/**
 *
 * @author yaqiang
 */
public class HYSPLITTrajDataInfo extends DataInfo implements ITrajDataInfo {

    // <editor-fold desc="Variables">
    /// <summary>
    /// Number of meteorological files
    /// </summary>
    public Integer meteoFileNum;
    /// <summary>
    /// Number of trajectories
    /// </summary>
    public int trajNum;
    private int endPointNum;
    /// <summary>
    /// Trajectory direction - foreward or backward
    /// </summary>
    public String trajDirection;
    /// <summary>
    /// Vertical motion
    /// </summary>
    public String verticalMotion;
    /// <summary>
    /// Information list of trajectories
    /// </summary>
    private List<TrajectoryInfo> trajInfoList;
    /// <summary>
    /// Number of variables
    /// </summary>
    public int varNum;
    /// <summary>
    /// Variable name list
    /// </summary>
    public List<String> varNames;
    private String[] inVarNames;
    private List<DataTable> dataTables;
    // </editor-fold>
    // <editor-fold desc="Constructor">

    /**
     * Constructor
     */
    public HYSPLITTrajDataInfo() {
        this.meteoDataType = MeteoDataType.HYSPLIT_TRAJ;
        initVariables();
    }

    private void initVariables() {
        trajInfoList = new ArrayList<>();
        varNames = new ArrayList<>();
        trajNum = 0;
        inVarNames = new String[]{"time", "run_hour", "lat", "lon", "height"};
    }
    // </editor-fold>
    // <editor-fold desc="Get Set Methods">
    @Override
    public String getXVarName() {
        return "lon";
    }

    @Override
    public String getYVarName() {
        return "lat";
    }

    @Override
    public String getZVarName() {
        return "height";
    }

    @Override
    public String getTVarName() {
        return "time";
    }

    @Override
    public List<TrajectoryInfo> getTrajInfoList() {
        return this.trajInfoList;
    }

    /**
     * Get data table list
     * @return Data table list
     */
    @Override
    public List<DataTable> getDataTables(){
        return this.dataTables;
    }
    // </editor-fold>
    // <editor-fold desc="Methods">

    @Override
    public void readDataInfo(String fileName) {
        BufferedReader sr = null;
        try {
            this.setFileName(fileName);
            String aLine;
            String[] dataArray;
            int i;
            initVariables();
            List<Double> times = new ArrayList<>();
            sr = new BufferedReader(new FileReader(new File(fileName)));
            //Record #1
            aLine = sr.readLine().trim();
            dataArray = aLine.split("\\s+");
            meteoFileNum = Integer.parseInt(dataArray[0]);
            //Record #2
            for (i = 0; i < meteoFileNum; i++) {
                sr.readLine();
            }
            //Record #3
            aLine = sr.readLine().trim();
            dataArray = aLine.split("\\s+");
            trajNum = Integer.parseInt(dataArray[0]);
            trajDirection = dataArray[1];
            verticalMotion = dataArray[2];
            //Record #4  
            TrajectoryInfo aTrajInfo;
            for (i = 0; i < trajNum; i++) {
                aLine = sr.readLine().trim();
                dataArray = aLine.split("\\s+");
                int y = Integer.parseInt(dataArray[0]);
                if (y < 100) {
                    if (y > 50) {
                        y = 1900 + y;
                    } else {
                        y = 2000 + y;
                    }
                }
                LocalDateTime tt = LocalDateTime.of(y, Integer.parseInt(dataArray[1]),
                        Integer.parseInt(dataArray[2]), Integer.parseInt(dataArray[3]), 0, 0);

                if (times.isEmpty()) {
                    times.add(JDateUtil.toOADate(tt));
                }
                aTrajInfo = new TrajectoryInfo();
                aTrajInfo.startTime = tt;
                aTrajInfo.startLat = Float.parseFloat(dataArray[4]);
                aTrajInfo.startLon = Float.parseFloat(dataArray[5]);
                aTrajInfo.startHeight = Float.parseFloat(dataArray[6]);
                trajInfoList.add(aTrajInfo);
            }
            Dimension tdim = new Dimension(DimensionType.T);
            tdim.setValues(times);
            //Record #5
            aLine = sr.readLine().trim();
            dataArray = aLine.split("\\s+");
            this.varNum = Integer.parseInt(dataArray[0]);
            if (varNum > dataArray.length - 1) {
                varNum = dataArray.length - 1;
            }
            for (i = 0; i < varNum; i++) {
                varNames.add(dataArray[i + 1]);
            }
            sr.close();
            
            //Read data table list
            this.dataTables = this.readTable();
            this.endPointNum = 0;
            for (DataTable table : this.dataTables){
                if (this.endPointNum < table.getRowCount()){
                    this.endPointNum = table.getRowCount();
                }
            }

            //Dimensions
            Dimension trajDim = new Dimension(DimensionType.OTHER);
            trajDim.setName("trajectory");
            trajDim.setLength(this.trajNum);
            this.addDimension(trajDim);
            Dimension obsDim = new Dimension(DimensionType.OTHER);
            obsDim.setName("obs");
            obsDim.setLength(this.endPointNum);
            this.addDimension(obsDim);

            //Variables
            for (String vName : this.inVarNames) {
                Variable var = new Variable();
                var.setName(vName);
                switch (vName) {
                    case "time":
                        var.setDataType(DataType.DATE);
                        break;
                    case "run_hour":
                        var.setDataType(DataType.INT);
                        break;
                    default:
                        var.setDataType(DataType.FLOAT);
                        break;
                }
                var.addDimension(trajDim);
                var.addDimension(obsDim);
                var.addAttribute("long_name", vName);
                this.addVariable(var);
            }
            for (String vName : this.varNames) {
                Variable var = new Variable();
                var.setName(vName);
                var.setDataType(DataType.FLOAT);
                var.addDimension(trajDim);
                var.addDimension(obsDim);
                var.addAttribute("long_name", vName);
                var.setStation(true);
                this.addVariable(var);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (sr != null) {
                    sr.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Get global attributes
     *
     * @return Global attributes
     */
    @Override
    public List<Attribute> getGlobalAttributes() {
        return new ArrayList<>();
    }

    @Override
    public String generateInfoText() {
        String dataInfo = "";
        int i;
        dataInfo += "File Name: " + this.getFileName();
        dataInfo += System.getProperty("line.separator") + "Trajectory number = " + String.valueOf(this.trajNum);
        dataInfo += System.getProperty("line.separator") + "Trajectory direction = " + trajDirection;
        dataInfo += System.getProperty("line.separator") + "Vertical motion =" + verticalMotion;
        dataInfo += System.getProperty("line.separator") + "Number of diagnostic output variables = "
                + String.valueOf(varNum);
        dataInfo += System.getProperty("line.separator") + "Variables:";
        for (i = 0; i < varNum; i++) {
            dataInfo += " " + varNames.get(i);
        }
        dataInfo += System.getProperty("line.separator") + System.getProperty("line.separator") + "Trajectories:";
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        for (TrajectoryInfo aTrajInfo : trajInfoList) {
            dataInfo += System.getProperty("line.separator") + "  " + format.format(aTrajInfo.startTime)
                    + "  " + String.valueOf(aTrajInfo.startLat) + "  " + String.valueOf(aTrajInfo.startLon)
                    + "  " + String.valueOf(aTrajInfo.startHeight);
        }

        dataInfo += System.getProperty("line.separator") + super.generateInfoText();

        return dataInfo;
    }

    /**
     * Read array data of a variable
     *
     * @param varName Variable name
     * @return Array data
     */
    @Override
    public Array read(String varName) {
        int[] origin = new int[]{0, 0};
        int[] size = new int[]{this.trajNum, this.endPointNum};
        int[] stride = new int[]{1, 1};

        Array r = read(varName, origin, size, stride);
        
        return r;
    }

    /**
     * Read array data of the variable
     *
     * @param varName Variable name
     * @param origin The origin array
     * @param size The size array
     * @param stride The stride array
     * @return Array data
     */
    @Override
    public Array read(String varName, int[] origin, int[] size, int[] stride) {
        try {
            DataColumn col = this.dataTables.get(0).findColumn(varName);
            DataType dtype = DataType.FLOAT;
            switch (col.getDataType()){
                case DATE:
                    dtype = DataType.DOUBLE;
                    break;
                case INT:
                    dtype = DataType.INT;
                    break;
            }
            Section section = new Section(origin, size, stride);
            Array array = Array.factory(dtype, section.getShape());
            Range trajRange = section.getRange(0);
            Range obsRange = section.getRange(1);
            Index index = array.getIndex();
            for (int trajIdx = trajRange.first(); trajIdx <= trajRange.last(); trajIdx += trajRange.stride()){
                DataTable dTable = this.dataTables.get(trajIdx);
                ColumnData colData = dTable.getColumnData(varName);
                for (int obsIdx = obsRange.first(); obsIdx <= obsRange.last(); obsIdx += obsRange.stride()){
                    if (colData.size() > obsIdx)
                        if (col.getDataType() == DataType.DATE) {
                            array.setObject(index, JDateUtil.toOADate((LocalDateTime) colData.getValue(obsIdx)));
                        } else {
                            array.setObject(index, colData.getValue(obsIdx));
                        }
                    else
                        array.setObject(index, Double.NaN);
                    index.incr();
                }
            }
            
            return array;
        } catch (InvalidRangeException ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    /**
     * Read trajectories as data table list.
     *
     * @return Data table list
     * @throws Exception
     */
    public List<DataTable> readTable() throws Exception {
        List<DataTable> tables = new ArrayList<>();
        for (int i = 0; i < this.trajNum; i++) {
            DataTable table = new DataTable();
            table.addColumn("time", DataType.DATE);
            table.addColumn("run_hour", DataType.FLOAT);
            table.addColumn("lat", DataType.FLOAT);
            table.addColumn("lon", DataType.FLOAT);
            table.addColumn("height", DataType.FLOAT);
            for (String vName : this.varNames) {
                table.addColumn(vName, DataType.FLOAT);
            }
            tables.add(table);
        }

        try {
            BufferedReader sr = new BufferedReader(new FileReader(this.getFileName()));
            String aLine;
            String[] dataArray, tempArray;
            int i;

            //Record #1
            sr.readLine();

            //Record #2
            for (i = 0; i < meteoFileNum; i++) {
                sr.readLine();
            }

            //Record #3
            sr.readLine();

            //Record #4             
            for (i = 0; i < trajNum; i++) {
                sr.readLine();
            }

            //Record #5
            sr.readLine();

            //Record #6
            int trajIdx;
            int dn = 12 + this.varNum;
            LocalDateTime tt;
            while (true) {
                aLine = sr.readLine();
                if (aLine == null) {
                    break;
                }
                if (aLine.isEmpty()) {
                    continue;
                }
                aLine = aLine.trim();
                dataArray = aLine.split("\\s+");
                if (dataArray.length < dn) {
                    aLine = sr.readLine().trim();
                    tempArray = aLine.split("\\s+");
                    dataArray = (String[]) DataConvert.resizeArray(dataArray, dn);
                    for (i = 0; i < tempArray.length; i++) {
                        dataArray[dn - tempArray.length + i] = tempArray[i];
                    }
                }
                trajIdx = Integer.parseInt(dataArray[0]) - 1;
                DataTable table = tables.get(trajIdx);
                table.addRow();
                int rowIdx = table.getRowCount() - 1;
                int y = Integer.parseInt(dataArray[2]);
                if (y < 100) {
                    if (y > 50) {
                        y = 1900 + y;
                    } else {
                        y = 2000 + y;
                    }
                }
                tt = LocalDateTime.of(y, Integer.parseInt(dataArray[3]),
                        Integer.parseInt(dataArray[4]), Integer.parseInt(dataArray[5]), 0, 0);
                table.setValue(rowIdx, "time", tt);
                table.setValue(rowIdx, "run_hour", Float.parseFloat(dataArray[8]));
                table.setValue(rowIdx, "lat", Float.parseFloat(dataArray[9]));
                table.setValue(rowIdx, "lon", Float.parseFloat(dataArray[10]));
                table.setValue(rowIdx, "height", Float.parseFloat(dataArray[11]));
                for (i = 12; i < dataArray.length; i++) {
                    table.setValue(rowIdx, i - 7, Float.parseFloat(dataArray[i]));
                }
            }
            sr.close();
        } catch (IOException ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        }

        return tables;
    }

    /**
     * Get XYDataset
     *
     * @param varIndex Variable index
     * @return XYDataset
     */
    public XYListDataset getXYDataset(int varIndex) {
        XYListDataset dataset = new XYListDataset();
        LocalDateTime tt = LocalDateTime.now();
        try {
            BufferedReader sr = new BufferedReader(new FileReader(new File(this.getFileName())));
            String aLine;
            String[] dataArray, tempArray;
            int i;

            //Record #1
            sr.readLine();

            //Record #2
            for (i = 0; i < meteoFileNum; i++) {
                sr.readLine();
            }

            //Record #3
            sr.readLine();

            //Record #4             
            for (i = 0; i < trajNum; i++) {
                sr.readLine();
            }

            //Record #5
            sr.readLine();

            //Record #6
            int TrajIdx;
            List<PointD> pList;
            List<List<PointD>> PointList = new ArrayList<>();
            for (i = 0; i < trajNum; i++) {
                pList = new ArrayList<>();
                PointList.add(pList);
            }
            PointD aPoint;
            //ArrayList polylines = new ArrayList();
            int dn = 12 + this.varNum;
            while (true) {
                aLine = sr.readLine();
                if (aLine == null) {
                    break;
                }
                if (aLine.isEmpty()) {
                    continue;
                }
                aLine = aLine.trim();
                dataArray = aLine.split("\\s+");
                if (dataArray.length < dn) {
                    aLine = sr.readLine().trim();
                    tempArray = aLine.split("\\s+");
                    dataArray = (String[]) DataConvert.resizeArray(dataArray, dn);
                    for (i = 0; i < tempArray.length; i++) {
                        dataArray[dn - tempArray.length + i] = tempArray[i];
                    }
                }
                TrajIdx = Integer.parseInt(dataArray[0]) - 1;
                int y = Integer.parseInt(dataArray[2]);
                if (y < 100) {
                    if (y > 50) {
                        y = 1900 + y;
                    } else {
                        y = 2000 + y;
                    }
                }
                tt = LocalDateTime.of(y, Integer.parseInt(dataArray[3]),
                        Integer.parseInt(dataArray[4]), Integer.parseInt(dataArray[5]), 0, 0);

                aPoint = new PointD();
                aPoint.X = JDateUtil.toOADate(tt);
                aPoint.Y = Double.parseDouble(dataArray[varIndex]);
                PointList.get(TrajIdx).add(aPoint);
            }

            for (i = 0; i < trajNum; i++) {
                int n = PointList.get(i).size();
                double[] xvs = new double[n];
                double[] yvs = new double[n];
                for (int j = 0; j < n; j++) {
                    xvs[j] = PointList.get(i).get(j).X;
                    yvs[j] = PointList.get(i).get(j).Y;
                }
                dataset.addSeries("Traj_" + String.valueOf(trajNum), xvs, yvs);
            }

            sr.close();
        } catch (IOException | NumberFormatException ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        }

        return dataset;
    }

    /**
     * Get XYDataset - X dimension is hours from start point
     *
     * @param varIndex Variable index
     * @return XYDataset
     */
    public XYListDataset getXYDataset_HourX(int varIndex) {
        XYListDataset dataset = new XYListDataset();
        LocalDateTime tt = LocalDateTime.now();
        try {
            BufferedReader sr = new BufferedReader(new FileReader(new File(this.getFileName())));
            String aLine;
            String[] dataArray, tempArray;
            int i;

            //Record #1
            sr.readLine();

            //Record #2
            for (i = 0; i < meteoFileNum; i++) {
                sr.readLine();
            }

            //Record #3
            sr.readLine();

            //Record #4             
            for (i = 0; i < trajNum; i++) {
                sr.readLine();
            }

            //Record #5
            sr.readLine();

            //Record #6
            int TrajIdx;
            List<PointD> pList;
            List<List<PointD>> PointList = new ArrayList<>();
            for (i = 0; i < trajNum; i++) {
                pList = new ArrayList<>();
                PointList.add(pList);
            }
            PointD aPoint;
            int dn = 12 + this.varNum;
            while (true) {
                aLine = sr.readLine();
                if (aLine == null) {
                    break;
                }
                if (aLine.isEmpty()) {
                    continue;
                }
                aLine = aLine.trim();
                dataArray = aLine.split("\\s+");
                if (dataArray.length < dn) {
                    aLine = sr.readLine().trim();
                    tempArray = aLine.split("\\s+");
                    dataArray = (String[]) DataConvert.resizeArray(dataArray, dn);
                    for (i = 0; i < tempArray.length; i++) {
                        dataArray[dn - tempArray.length + i] = tempArray[i];
                    }
                }
                TrajIdx = Integer.parseInt(dataArray[0]) - 1;
                int y = Integer.parseInt(dataArray[2]);
                if (y < 100) {
                    if (y > 50) {
                        y = 1900 + y;
                    } else {
                        y = 2000 + y;
                    }
                }
                tt = LocalDateTime.of(y, Integer.parseInt(dataArray[3]),
                        Integer.parseInt(dataArray[4]), Integer.parseInt(dataArray[5]), 0, 0);

                aPoint = new PointD();
                aPoint.X = JDateUtil.toOADate(tt);
                aPoint.Y = Double.parseDouble(dataArray[varIndex]);
                PointList.get(TrajIdx).add(aPoint);
            }

            for (i = 0; i < trajNum; i++) {
                int n = PointList.get(i).size();
                double[] xvs = new double[n];
                double[] yvs = new double[n];
                LocalDateTime cdate, sdate = LocalDateTime.now();
                for (int j = 0; j < n; j++) {
                    cdate = JDateUtil.fromOADate(PointList.get(i).get(j).X);
                    if (j == 0) {
                        sdate = cdate;
                        xvs[j] = 0;
                    } else {
                        xvs[j] = Duration.between(sdate, cdate).toHours();
                    }
                    yvs[j] = PointList.get(i).get(j).Y;
                }
                dataset.addSeries("Traj_" + String.valueOf(trajNum), xvs, yvs);
            }

            sr.close();
        } catch (IOException | NumberFormatException ex) {
            Logger.getLogger(HYSPLITTrajDataInfo.class.getName()).log(Level.SEVERE, null, ex);
        }

        return dataset;
    }

    // </editor-fold>
}
