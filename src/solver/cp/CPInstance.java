package solver.cp;

import ilog.cp.*;

import ilog.concert.*;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.Scanner;

public class CPInstance
{
  // BUSINESS parameters
  int numWeeks;
  int numDays;  
  int numEmployees;
  int numShifts;
  int numIntervalsInDay;
  int[][] minDemandDayShift;
  int minDailyOperation;
  
  // EMPLOYEE parameters
  int minConsecutiveWork;
  int maxDailyWork;
  int minWeeklyWork;
  int maxWeeklyWork;
  int maxConsecutiveNightShift;
  int maxTotalNightShift;

  // ILOG CP Solver
  IloCP cp;
    
  public CPInstance(String fileName)
  {
    try
    {
      try (Scanner read = new Scanner(new File(fileName))) {
        while (read.hasNextLine())
        {
          String line = read.nextLine();
          String[] values = line.split(" ");
          if(values[0].equals("Business_numWeeks:"))
          {
            numWeeks = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Business_numDays:"))
          {
            numDays = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Business_numEmployees:"))
          {
            numEmployees = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Business_numShifts:"))
          {
            numShifts = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Business_numIntervalsInDay:"))
          {
            numIntervalsInDay = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Business_minDemandDayShift:"))
          {
            int index = 1;
            minDemandDayShift = new int[numDays][numShifts];
            for(int d=0; d<numDays; d++)
              for(int s=0; s<numShifts; s++)
                minDemandDayShift[d][s] = Integer.parseInt(values[index++]);
          }
          else if(values[0].equals("Business_minDailyOperation:"))
          {
            minDailyOperation = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Employee_minConsecutiveWork:"))
          {
            minConsecutiveWork = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Employee_maxDailyWork:"))
          {
            maxDailyWork = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Employee_minWeeklyWork:"))
          {
            minWeeklyWork = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Employee_maxWeeklyWork:"))
          {
            maxWeeklyWork = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Employee_maxConsecutiveNigthShift:"))
          {
            maxConsecutiveNightShift = Integer.parseInt(values[1]);
          }
          else if(values[0].equals("Employee_maxTotalNigthShift:"))
          {
            maxTotalNightShift = Integer.parseInt(values[1]);
          }
        }
      }
    }
    catch (FileNotFoundException e)
    {
      System.out.println("Error: file not found " + fileName);
    }
  }

  public void solve()
  {
    try
    {
      cp = new IloCP();        
      // Important: Do not change! Keep these parameters as is
      cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
      // cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);   
  
      // Uncomment this: to set the solver output level if you wish
      // cp.setParameter(IloCP.IntParam.LogVerbosity, IloCP.ParameterValues.Quiet);

      // Assigned Shifts
      IloIntVar[][] assignments = new IloIntVar[numDays][numEmployees];
      for (int day = 0; day < numDays; day++) {
        for (int employee = 0; employee < numEmployees; employee++) {
          // 4 possible shifts (0 to 3), each employee can only be assigned to a single shift
          assignments[day][employee] = cp.intVar(0, numShifts - 1);
        }
      }
      
      // there is a certain minimum demand that needs to be met on the number of employees needed every day for every shift
      // minDemandDayShift[day][shift]
      for (int day = 0; day < numDays; day++) {
        for (int shift = 0; shift < numShifts - 1; shift++) {
          int demand = minDemandDayShift[day][shift];
          cp.add(cp.ge(cp.count(assignments[day], shift), demand));
        }
      }

      // the first 4 days of the schedule is treated specially where employees are assigned to unique shifts.
      for (int employee = 0; employee < numEmployees; employee++) {
        IloIntExpr[] clique = new IloIntExpr[4];
        clique[0] = assignments[0][employee];
        clique[1] = assignments[1][employee];
        clique[2] = assignments[2][employee];
        clique[3] = assignments[3][employee];

        // alldiff
        cp.add(cp.allDiff(clique));

        // same as alldiff, 0 + 1 + 2 + 3 = 6
        // test if any of these speed up
        cp.add(cp.le(cp.sum(clique), 6));
        cp.add(cp.ge(cp.sum(clique), 6));
        cp.add(cp.eq(cp.sum(clique), 6));
      }
      
      int[] workableHours = new int[]{0, 4, 5, 6, 7, 8};

      // Employees cannot work more than 8 hours per day and less than 4 hours per day
      IloIntVar[][] hoursWorked = new IloIntVar[numDays][numEmployees];
      for (int day = 0; day < numDays; day++) {
        for (int employee = 0; employee < numEmployees; employee++) {
          // must work either 0 or 20-40 hours
          hoursWorked[day][employee] = cp.intVar(workableHours);

          // must work zero hours if shift is zero
          // otherwise must work over 4 and under 8
          cp.add(cp.or(
              cp.and(
                      cp.eq(assignments[day][employee], 0),
                      cp.eq(hoursWorked[day][employee], 0)
              ),
              cp.and(
                cp.ge(hoursWorked[day][employee], minConsecutiveWork),
                cp.le(hoursWorked[day][employee], maxDailyWork)
              )
            )
          );
        }
      }

      // hours worked cannot exceed the standard 40-hours per week and it should not be less than 20-hours per week
      for (int week = 0; week < numWeeks; week++){
        for (int employee = 0; employee < numEmployees; employee++) {
          IloIntExpr[] hoursWorkedThisWeek = new IloIntExpr[7];
          for (int day = 0; day < hoursWorkedThisWeek.length; day++) {
            hoursWorkedThisWeek[day] = hoursWorked[week * 7 + day][employee];
          }
          // must work less than 40
          cp.add(cp.le(cp.sum(hoursWorkedThisWeek), 40));
          // must work more than 20
          cp.add(cp.ge(cp.sum(hoursWorkedThisWeek), 20));
        }
      }
//
//      // It is known that night shifts are stressful, therefore night shifts cannot follow each other
//      // max number of total night shifts per employee
//      for (int employee = 0; employee < numEmployees; employee++) {
//        IloIntExpr[] employeeShifts = new IloIntExpr[numDays];
//        for (int day = 0; day < numDays; day++) {
//          employeeShifts[day] = assignments[day][employee];
//        }
//        cp.add(cp.ge(cp.count(employeeShifts, 1), maxTotalNightShift));
//
//        for (int day = 0; day < numDays - maxConsecutiveNightShift; day++) {
//          IloIntExpr[] employeeConcecutiveShifts = new IloIntExpr[maxConsecutiveNightShift + 1];
//          for (int concec = 0; concec < maxConsecutiveNightShift + 1; concec++) {
//            employeeConcecutiveShifts[concec] = assignments[day + concec][employee];
//          }
//
//          cp.add(cp.le(cp.count(employeeConcecutiveShifts, 1), maxConsecutiveNightShift));
//        }
//      }
      
      
      
      if(cp.solve())
      {
        cp.printInformation();


        // beginED int[e][d] the hour employee e begins work on day d, -1 if not working
        // endED   int[e][d] the hour employee e ends work on day d, -1 if not working
        int[][] beginED = new int[numEmployees][numDays];
        int[][] endED = new int[numEmployees][numDays];

        int[][] solvedAssignments = new int[numEmployees][numDays];
        for (int employee = 0; employee < numEmployees; employee++) {
          for (int day = 0; day < numDays; day++) {
            solvedAssignments[employee][day] = (int)cp.getValue(assignments[day][employee]);
          }
        }          

        int[][] solvedHours = new int[numEmployees][numDays];
        for (int employee = 0; employee < numEmployees; employee++) {
          for (int day = 0; day < numDays; day++) {
            solvedHours[employee][day] = (int)cp.getValue(hoursWorked[day][employee]);
          }
        }

        // Fill beginED and endED arrays
        for (int employee = 0; employee < numEmployees; employee++) {
          for (int day = 0; day < numDays; day++) {
            if (solvedAssignments[employee][day] == 0) {
              beginED[employee][day] = -1;
              endED[employee][day] = -1;
            } else if (solvedAssignments[employee][day] == 1) {
              beginED[employee][day] = 0 ;
              endED[employee][day] = 0 + solvedHours[employee][day];
            } else if (solvedAssignments[employee][day] == 2) {
              beginED[employee][day] = 8;
              endED[employee][day] = 8 + solvedHours[employee][day];
            } else if (solvedAssignments[employee][day] == 3) {
              beginED[employee][day] = 16;
              endED[employee][day] = 16 + solvedHours[employee][day];
            }
          }
        }
        
        

        // Uncomment this: for poor man's Gantt Chart to display schedules
        prettyPrint(numEmployees, numDays, beginED, endED);	
      }
      else
      {
        System.out.println("No Solution found!");
        System.out.println("Number of fails: " + cp.getInfo(IloCP.IntInfo.NumberOfFails));
      }
    }
    catch(IloException e)
    {
      System.out.println("Error: " + e);
    }
  }

  // SK: technically speaking, the model with the global constaints
  // should result in fewer number of fails. In this case, the problem 
  // is so simple that, the solver is able to re-transform the model 
  // and replace inequalities with the global all different constrains.
  // Therefore, the results don't really differ
  void solveAustraliaGlobal()
  {
    String[] Colors = {"red", "green", "blue"};
    try 
    {
      cp = new IloCP();
      IloIntVar WesternAustralia = cp.intVar(0, 3);
      IloIntVar NorthernTerritory = cp.intVar(0, 3);
      IloIntVar SouthAustralia = cp.intVar(0, 3);
      IloIntVar Queensland = cp.intVar(0, 3);
      IloIntVar NewSouthWales = cp.intVar(0, 3);
      IloIntVar Victoria = cp.intVar(0, 3);
      
      IloIntExpr[] clique1 = new IloIntExpr[3];
      clique1[0] = WesternAustralia;
      clique1[1] = NorthernTerritory;
      clique1[2] = SouthAustralia;
      
      IloIntExpr[] clique2 = new IloIntExpr[3];
      clique2[0] = Queensland;
      clique2[1] = NorthernTerritory;
      clique2[2] = SouthAustralia;
      
      IloIntExpr[] clique3 = new IloIntExpr[3];
      clique3[0] = Queensland;
      clique3[1] = NewSouthWales;
      clique3[2] = SouthAustralia;
      
      IloIntExpr[] clique4 = new IloIntExpr[3];
      clique4[0] = Queensland;
      clique4[1] = Victoria;
      clique4[2] = SouthAustralia;
      
      cp.add(cp.allDiff(clique1));
      cp.add(cp.allDiff(clique2));
      cp.add(cp.allDiff(clique3));
      cp.add(cp.allDiff(clique4));
      
	  cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
	  cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);   
	  
      if (cp.solve())
      {    
         System.out.println();
         System.out.println( "WesternAustralia:    " + Colors[(int)cp.getValue(WesternAustralia)]);
         System.out.println( "NorthernTerritory:   " + Colors[(int)cp.getValue(NorthernTerritory)]);
         System.out.println( "SouthAustralia:      " + Colors[(int)cp.getValue(SouthAustralia)]);
         System.out.println( "Queensland:          " + Colors[(int)cp.getValue(Queensland)]);
         System.out.println( "NewSouthWales:       " + Colors[(int)cp.getValue(NewSouthWales)]);
         System.out.println( "Victoria:            " + Colors[(int)cp.getValue(Victoria)]);
      }
      else
      {
        System.out.println("No Solution found!");
      }
    } catch (IloException e) 
    {
      System.out.println("Error: " + e);
    }
  }
  
  void solveAustraliaBinary()
  {
    String[] Colors = {"red", "green", "blue"};
    try 
    {
      cp = new IloCP();
      IloIntVar WesternAustralia = cp.intVar(0, 3);
      IloIntVar NorthernTerritory = cp.intVar(0, 3);
      IloIntVar SouthAustralia = cp.intVar(0, 3);
      IloIntVar Queensland = cp.intVar(0, 3);
      IloIntVar NewSouthWales = cp.intVar(0, 3);
      IloIntVar Victoria = cp.intVar(0, 3);
      
      cp.add(cp.neq(WesternAustralia , NorthernTerritory)); 
      cp.add(cp.neq(WesternAustralia , SouthAustralia)); 
      cp.add(cp.neq(NorthernTerritory , SouthAustralia));
      cp.add(cp.neq(NorthernTerritory , Queensland));
      cp.add(cp.neq(SouthAustralia , Queensland)); 
      cp.add(cp.neq(SouthAustralia , NewSouthWales)); 
      cp.add(cp.neq(SouthAustralia , Victoria)); 
      cp.add(cp.neq(Queensland , NewSouthWales));
      cp.add(cp.neq(NewSouthWales , Victoria)); 
      
	  cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
	  cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);   
	  
      if (cp.solve())
      {    
         System.out.println();
         System.out.println( "WesternAustralia:    " + Colors[(int)cp.getValue(WesternAustralia)]);
         System.out.println( "NorthernTerritory:   " + Colors[(int)cp.getValue(NorthernTerritory)]);
         System.out.println( "SouthAustralia:      " + Colors[(int)cp.getValue(SouthAustralia)]);
         System.out.println( "Queensland:          " + Colors[(int)cp.getValue(Queensland)]);
         System.out.println( "NewSouthWales:       " + Colors[(int)cp.getValue(NewSouthWales)]);
         System.out.println( "Victoria:            " + Colors[(int)cp.getValue(Victoria)]);
      }
      else
      {
        System.out.println("No Solution found!");
      }
    } catch (IloException e) 
    {
      System.out.println("Error: " + e);
    }
  }

  void solveSendMoreMoney()
  {
    try 
    {
      // CP Solver
      cp = new IloCP();
	
      // SEND MORE MONEY
      IloIntVar S = cp.intVar(1, 9);
      IloIntVar E = cp.intVar(0, 9);
      IloIntVar N = cp.intVar(0, 9);
      IloIntVar D = cp.intVar(0, 9);
      IloIntVar M = cp.intVar(1, 9);
      IloIntVar O = cp.intVar(0, 9);
      IloIntVar R = cp.intVar(0, 9);
      IloIntVar Y = cp.intVar(0, 9);
      
      IloIntVar[] vars = new IloIntVar[]{S, E, N, D, M, O, R, Y};
      cp.add(cp.allDiff(vars));
      
      //                1000 * S + 100 * E + 10 * N + D 
      //              + 1000 * M + 100 * O + 10 * R + E
      //  = 10000 * M + 1000 * O + 100 * N + 10 * E + Y 
      
      IloIntExpr SEND = cp.sum(cp.prod(1000, S), cp.sum(cp.prod(100, E), cp.sum(cp.prod(10, N), D)));
      IloIntExpr MORE   = cp.sum(cp.prod(1000, M), cp.sum(cp.prod(100, O), cp.sum(cp.prod(10,R), E)));
      IloIntExpr MONEY  = cp.sum(cp.prod(10000, M), cp.sum(cp.prod(1000, O), cp.sum(cp.prod(100, N), cp.sum(cp.prod(10,E), Y))));
      
      cp.add(cp.eq(MONEY, cp.sum(SEND, MORE)));
      
      // Solver parameters
      cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);
      if(cp.solve())
      {
        System.out.println("  " + cp.getValue(S) + " " + cp.getValue(E) + " " + cp.getValue(N) + " " + cp.getValue(D));
        System.out.println("  " + cp.getValue(M) + " " + cp.getValue(O) + " " + cp.getValue(R) + " " + cp.getValue(E));
        System.out.println(cp.getValue(M) + " " + cp.getValue(O) + " " + cp.getValue(N) + " " + cp.getValue(E) + " " + cp.getValue(Y));
      }
      else
      {
        System.out.println("No Solution!");
      }
    } catch (IloException e) 
    {
      System.out.println("Error: " + e);
    }
  }
  
 /**
   * Poor man's Gantt chart.
   * author: skadiogl
   *
   * Displays the employee schedules on the command line. 
   * Each row corresponds to a single employee. 
   * A "+" refers to a working hour and "." means no work
   * The shifts are separated with a "|"
   * The days are separated with "||"
   * 
   * This might help you analyze your solutions. 
   * 
   * @param numEmployees the number of employees
   * @param numDays the number of days
   * @param beginED int[e][d] the hour employee e begins work on day d, -1 if not working
   * @param endED   int[e][d] the hour employee e ends work on day d, -1 if not working
   */
  void prettyPrint(int numEmployees, int numDays, int[][] beginED, int[][] endED)
  {
    for (int e = 0; e < numEmployees; e++)
    {
      System.out.print("E"+(e+1)+": ");
      if(e < 9) System.out.print(" ");
      for (int d = 0; d < numDays; d++)
      {
        for(int i=0; i < numIntervalsInDay; i++)
        {
          if(i%8==0)System.out.print("|");
          if (beginED[e][d] != endED[e][d] && i >= beginED[e][d] && i < endED[e][d]) System.out.print("+");
          else  System.out.print(".");
        }
        System.out.print("|");
      }
      System.out.println(" ");
    }
  }

}
