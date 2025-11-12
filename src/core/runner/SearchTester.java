package core.runner;

import algs4.util.StopwatchCPU;
import core.problem.Problem;
import core.problem.ProblemType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import core.solver.algorithm.heuristic.HeuristicType;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Scanner;
import core.runner.EngineFeeder;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.runner.PuzzleFeeder;

import static core.solver.algorithm.heuristic.HeuristicType.*;

/**
 * 对学生的搜索算法进行检测的主程序
 * arg0: 问题输入样例      resources/npuzzle.txt
 * arg1: 问题类型         NPUZZLE
 * arg2: 项目的哪个阶段    1
 * arg3: 各小组的Feeder   stud.g01.runner.PuzzleFeeder
 */

public final class SearchTester {
    //同学们可以根据自己的需要，随意修改。
    public static void main(String[] args) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException, FileNotFoundException {
        // 参数验证
        if (args.length < 4) {
            System.out.println("Usage: java SearchTester <problem_file> <problem_type> <step> <feeder_class> [algorithm]");
            System.out.println("Example: java SearchTester resources/npuzzle.txt NPUZZLE 1 stud.g01.runner.PuzzleFeeder");
            System.out.println("Algorithm options: astar (default), idastar, both");
            return;
        }

        // 解析算法参数
        String algorithm = "astar"; // 默认算法
        if (args.length >= 5) {
            algorithm = args[4].toLowerCase();
        }

        // 根据args[3]提供的类名生成学生的EngineFeeder对象
        EngineFeeder feeder = (EngineFeeder)
                Class.forName(args[3])
                        .getDeclaredConstructor().newInstance();

        // 从文件读入所有输入样例的文本
        Scanner scanner = new Scanner(new File(args[0]));
        ArrayList<String> problemLines = getProblemLines(scanner);
        scanner.close();

        // feeder从输入样例文本获取问题的所有实例
        ArrayList<Problem> problems = feeder.getProblems(problemLines);

        // 当前问题的类型 args[1]
        ProblemType type = ProblemType.valueOf(args[1]);
        // 任务第几阶段 args[2]
        int step = Integer.parseInt(args[2]);

        System.out.println("=== 开始测试 ===");
        System.out.println("问题类型: " + type);
        System.out.println("阶段: " + step);
        System.out.println("问题数量: " + problems.size());
        System.out.println("算法: " + algorithm);
        System.out.println();

        // 根据问题类型和当前阶段，获取所有启发函数的类型
        ArrayList<HeuristicType> heuristics = getHeuristicTypes(type, step);

        for (HeuristicType heuristicType : heuristics) {
            System.out.println("=== 使用启发式: " + heuristicType + " ===");

            // 根据算法参数选择要运行的算法
            if (algorithm.equals("astar") || algorithm.equals("both")) {
                System.out.println("--- A* 算法 ---");
                solveProblems(problems, feeder.getAStar(heuristicType), heuristicType);
            }

            if (algorithm.equals("idastar") || algorithm.equals("both")) {
                System.out.println("--- IdAStar 算法 ---");
                solveProblemsWithIdaStar(problems, feeder, heuristicType);
            }

            System.out.println();
        }

        // 在SearchTester.java的main方法中，在参数解析后添加：

        // 检查是否运行性能测试
        boolean runPerformanceTest = args.length >= 6 && "performance".equals(args[5]);
        boolean runComparativeTest = args.length >= 6 && "comparative".equals(args[5]);
        boolean runDetailedTest = args.length >= 6 && "detailed".equals(args[5]);

        if (runPerformanceTest && feeder instanceof stud.g01.runner.PuzzleFeeder) {
            // 运行NPuzzle性能测试
            stud.g01.runner.NPuzzlePerformanceRunner runner =
                    new stud.g01.runner.NPuzzlePerformanceRunner((stud.g01.runner.PuzzleFeeder) feeder);
            runner.runStageTest(step);
            return;
        } else if (runComparativeTest && feeder instanceof stud.g01.runner.PuzzleFeeder) {
            // 运行比较测试（使用第一个问题）
            if (!problems.isEmpty()) {
                stud.g01.runner.NPuzzlePerformanceRunner runner =
                        new stud.g01.runner.NPuzzlePerformanceRunner((stud.g01.runner.PuzzleFeeder) feeder);
                runner.runComparativeTest(problems.get(0));
            }
            return;
        } else if (runDetailedTest && feeder instanceof stud.g01.runner.PuzzleFeeder) {
            // 运行详细测试
            if (!problems.isEmpty()) {
                stud.g01.runner.NPuzzlePerformanceRunner runner =
                        new stud.g01.runner.NPuzzlePerformanceRunner((stud.g01.runner.PuzzleFeeder) feeder);
                // 使用默认算法配置进行详细测试
                runner.runDetailedTest(problems.get(0), "A*", HeuristicType.MANHATTAN);
            }
            return;
        }

        // 添加阶段三性能测试选项
        if (args.length >= 6 && "pattern_performance".equals(args[5])) {
            if (feeder instanceof stud.g01.runner.PuzzleFeeder) {
                ((stud.g01.runner.PuzzleFeeder) feeder).runPatternDatabasePerformanceTest();
            }
            return;
        }

        // 阶段三：使用模式数据库的优化测试
        if (step == 3) {
            runStage3Tests(problems, feeder);
        }
    }



    /**
     * 根据问题类型和当前阶段，获取所有启发函数的类型
     * @param type
     * @param step
     * @return
     */
    private static ArrayList<HeuristicType> getHeuristicTypes(ProblemType type, int step) {
        //求解当前问题在当前阶段可用的启发函数类型列表
        ArrayList<HeuristicType> heuristics = new ArrayList<>();
        //根据不同的问题类型，进行不同的测试
        if (type == ProblemType.PATHFINDING) {
            heuristics.add(PF_GRID);
            heuristics.add(PF_EUCLID);
        }
        else if (type == ProblemType.NPUZZLE) {
            //NPuzzle问题的第一阶段，使用不在位将牌和曼哈顿距离
            if (step == 1) {
                heuristics.add(MISPLACED);
                heuristics.add(MANHATTAN);
            }
            //NPuzzle问题的其他阶段
            else {
                heuristics.add(MANHATTAN); // 默认使用曼哈顿距离
                // 可以根据需要添加其他启发式
            }
        }
        // 可以添加其他问题类型的处理
        else {
            System.out.println("未知的问题类型: " + type);
        }
        return heuristics;
    }

    /**
     * 使用给定的searcher，求解问题集合中的所有问题，同时使用解检测器对求得的解进行检测
     * @param problems     问题集合
     * @param searcher     searcher
     * @param heuristicType 使用哪种启发函数？
     */
    private static void solveProblems(ArrayList<Problem> problems, AbstractSearcher searcher, HeuristicType heuristicType) {
        int problemIndex = 1;
        for (Problem problem : problems) {
            System.out.println("--- 问题 " + problemIndex + " ---");

            // 检查问题是否有解
            if (!problem.solvable()) {
                System.out.println("此问题无解，跳过");
                problemIndex++;
                continue;
            }

            // 显示初始状态和目标状态
            System.out.println("初始状态:");
            problem.root().getState().draw();
            System.out.println("目标状态:");
            ((NPuzzleProblem)problem).getGoal().draw();

            // 使用搜索算法求解问题
            StopwatchCPU timer1 = new StopwatchCPU();
            Deque<Node> path = searcher.search(problem);
            double time1 = timer1.elapsedTime();

            if (path == null) {
                System.out.println("未找到解" + "，执行时间: " + time1 + "s，" +
                        "生成节点: " + searcher.nodesGenerated() + "，" +
                        "扩展节点: " + searcher.nodesExpanded());
            } else {
                // 解路径的可视化
                problem.showSolution(path);

                System.out.println("启发函数: " + heuristicType +
                        "，解路径长度: " + (path.size() - 1) +
                        "，执行时间: " + time1 + "s，" +
                        "生成节点: " + searcher.nodesGenerated() +
                        "，扩展节点: " + searcher.nodesExpanded());
            }
            System.out.println();
            problemIndex++;
        }
    }

    /**
     * 使用IdAStar求解问题
     */
    /**
     * 使用IdAStar求解问题
     */
    private static void solveProblemsWithIdaStar(ArrayList<Problem> problems, EngineFeeder feeder, HeuristicType heuristicType) {
        int problemIndex = 1;
        AbstractSearcher searcher = feeder.getIdaStar(heuristicType);

        for (Problem problem : problems) {
            System.out.println("--- 问题 " + problemIndex + " (IdAStar) ---");

            // 检查问题是否有解
            if (!problem.solvable()) {
                System.out.println("此问题无解，跳过");
                problemIndex++;
                continue;
            }

            // 显示初始状态和目标状态
            System.out.println("初始状态:");
            problem.root().getState().draw();
            System.out.println("目标状态:");
            if (problem instanceof stud.g01.problem.npuzzle.NPuzzleProblem) {
                ((stud.g01.problem.npuzzle.NPuzzleProblem) problem).getGoal().draw();
            }

            // 使用IdAStar搜索算法求解问题
            StopwatchCPU timer = new StopwatchCPU();
            Deque<core.solver.queue.Node> path = searcher.search(problem);
            double time = timer.elapsedTime();

            if (path == null) {
                System.out.println("未找到解" + "，执行时间: " + time + "s，" +
                        "生成节点: " + searcher.nodesGenerated() + "，" +
                        "扩展节点: " + searcher.nodesExpanded());
            } else {
                // 解路径的可视化
                problem.showSolution(path);

                System.out.println("启发函数: " + heuristicType +
                        "，解路径长度: " + (path.size() - 1) +
                        "，执行时间: " + time + "s，" +
                        "生成节点: " + searcher.nodesGenerated() +
                        "，扩展节点: " + searcher.nodesExpanded());
            }
            System.out.println();
            problemIndex++;
        }
    }

    /**
     * 从文件读入问题实例的字符串，放入字符串数组里
     * @param scanner
     * @return
     */
    public static ArrayList<String> getProblemLines(Scanner scanner) {
        ArrayList<String> lines = new ArrayList<>();
        while (scanner.hasNext()){
            String line = scanner.nextLine().trim();
            // 跳过空行和注释行
            if (!line.isEmpty() && !line.startsWith("#")) {
                lines.add(line);
            }
        }
        return lines;
    }



    /**
     * 阶段三专用测试
     */
    private static void runStage3Tests(ArrayList<Problem> problems, EngineFeeder feeder) {
        System.out.println("=== 阶段三：模式数据库性能测试 ===");

        if (feeder instanceof stud.g01.runner.PuzzleFeeder) {
            PuzzleFeeder puzzleFeeder = (PuzzleFeeder) feeder;

            // 运行模式数据库构建性能测试
            puzzleFeeder.runPatternDatabasePerformanceTest();

            System.out.println("\n=== 阶段三：问题求解测试 ===");

            // 测试阶段三的复杂问题
            Problem stage3Problem = createStage3Problem();
            if (stage3Problem != null && stage3Problem.solvable()) {
                System.out.println("\n--- 求解阶段三复杂问题 ---");
                stage3Problem.root().getState().draw();

                // 比较不同构建方法的性能
                testStage3Configuration(stage3Problem, puzzleFeeder, "A*", HeuristicType.PATTERN_78);;
                testStage3Configuration(stage3Problem, puzzleFeeder, "A*", HeuristicType.PATTERN_663);
            }
        }
    }

    /**
     * 创建阶段三的复杂问题实例
     */
    private static Problem createStage3Problem() {
        try {
            // 阶段三的复杂实例
            int[] initial = {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0};
            int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};

            stud.g01.problem.npuzzle.PuzzleBoard initialState =
                    new stud.g01.problem.npuzzle.PuzzleBoard(4, initial);
            stud.g01.problem.npuzzle.PuzzleBoard goalState =
                    new stud.g01.problem.npuzzle.PuzzleBoard(4, goal);

            return new stud.g01.problem.npuzzle.NPuzzleProblem(initialState, goalState, 4);
        } catch (Exception e) {
            System.out.println("创建阶段三问题失败: " + e.getMessage());
            return null;
        }
    }

    private static void testStage3Configuration(Problem problem, PuzzleFeeder feeder,
                                                String algorithm, HeuristicType heuristic) {
        System.out.println("\n算法: " + algorithm + ", 启发式: " + heuristic);

        Predictor predictor = feeder.getPredictor(heuristic);
        AbstractSearcher searcher = feeder.getSearcher(algorithm, heuristic);

        long startTime = System.currentTimeMillis();
        java.util.Deque<core.solver.queue.Node> path = searcher.search(problem);
        long endTime = System.currentTimeMillis();

        if (path != null) {
            System.out.printf("? 解长度: %d, 时间: %.3fs, 生成节点: %d, 扩展节点: %d\n",
                    path.size() - 1, (endTime - startTime) / 1000.0,
                    searcher.nodesGenerated(), searcher.nodesExpanded());

            if ((endTime - startTime) <= 60000) {
                System.out.println("? 满足1分钟时限要求");
            } else {
                System.out.println("? 超出1分钟时限");
            }
        } else {
            System.out.println("? 未找到解");
        }
    }
}