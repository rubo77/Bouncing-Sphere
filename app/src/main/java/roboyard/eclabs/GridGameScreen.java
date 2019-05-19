package roboyard.eclabs;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

import java.util.*;
import java.util.HashMap;
import java.util.Map;

import roboyard.eclabs.solver.ISolver;
import roboyard.eclabs.solver.SolverRR;
import roboyard.eclabs.solver.SolverDD;
import roboyard.pm.ia.GameSolution;
import roboyard.pm.ia.IGameMove;
import roboyard.pm.ia.ricochet.RRGameMove;

/**
 * Created by Alain on 25/02/2015.
 */
public class GridGameScreen extends GameScreen {
    private Canvas canvasGrid;

    private int gridLineThickness = 4; // thickness of walls
    private ColorFilter wallColor = new PorterDuffColorFilter(Color.rgb(44, 96, 0), PorterDuff.Mode.SRC_ATOP); // green
    private boolean isSolved = false;
    private int solutionMoves = 0; // store the current optimal solution globally
    private int numSolutionClicks = 0; // count how often you clicked on the solution button, each time the shown count goes down by one
    private int showSolutionAtHint = 5; // interval between the first hint and the current optimal solution (will be set to random 3..5 later

    private static int goodPuzzleMinMoves = 8; // below this number of moves there is a special hint shown from the start
    private static int simplePuzzleMinMoves = 6; // below this threshold a new puzzle is generated

    private static String requestToast = ""; // this can be set from outside to set the text for a popup

    private ISolver solver;

    private boolean autoSaved = false;

    private ArrayList gridElements;
    private int imageGridID;
    private boolean imageLoaded = false;

    private String mapPath = "";

    private int xGrid = 0;
    private int yGrid = 100;

    private float gridSpace = 0;

    private int timeCpt = 0;
    private int nbCoups = 0;
    private long prevTime;

    private static boolean solverBFS = false; // default IDDFS (faster)
    private static String UserLevel="Advanced";
    private static ArrayList<GridElement> currentMap;

    private int IAMovesNumber = 0;

    private boolean mustStartNext = false;

    private ArrayList<IGameMove> moves = null;

    private Thread t = null;

    private GameMovementInterface gmi;
    private Bitmap bitmapGrid;
    RenderManager currentRenderManager;
    Map<String, Drawable> drawables = new HashMap<String, Drawable>();
    Map<String, Integer> colors = new HashMap<String, Integer>();
    private ArrayList<Move> allMoves= new ArrayList<>();

    private GameButtonGeneral buttonSolve;
    private GameButtonGoto buttonSave;

    public GridGameScreen(GameManager gameManager){
        super(gameManager);

        gridSpace = (float)(67.5 * gameManager.getScreenWidth() /1080);
        xGrid = 0;
        yGrid = 1080/5;

        Bitmap.Config conf = Bitmap.Config.ARGB_4444;

        bitmapGrid = Bitmap.createBitmap((int)(16 * gridSpace), (int) (16 * gridSpace), conf);
        canvasGrid = new Canvas(bitmapGrid);
        currentRenderManager = gameManager.getRenderManager();

        prevTime = System.currentTimeMillis();

        gameManager.getRenderManager().loadImage(R.drawable.rj);
        gameManager.getRenderManager().loadImage(R.drawable.rb);
        gameManager.getRenderManager().loadImage(R.drawable.rv);
        gameManager.getRenderManager().loadImage(R.drawable.rr);

    }

    public static String getLevel() {
        return GridGameScreen.UserLevel;
    }

    public static void setLevel(String UserLevel) {
        GridGameScreen.UserLevel = UserLevel;
        if(UserLevel=="Beginner") {
            GridGameScreen.goodPuzzleMinMoves = 6;
            GridGameScreen.simplePuzzleMinMoves = 4;
        } else if(UserLevel=="Advanced") {
            GridGameScreen.goodPuzzleMinMoves = 8;
            GridGameScreen.simplePuzzleMinMoves = 6;
        } else if(UserLevel=="Insane") {
            GridGameScreen.goodPuzzleMinMoves = 10;
            GridGameScreen.simplePuzzleMinMoves = 10;
            GridGameScreen.requestToast = "Insane level will generate a fitting puzzle. This can take a while. In case the solver gets stuck, press >>";
        }
    }

    public static ArrayList<GridElement> getMap() {
        return GridGameScreen.currentMap;
    }

    public static void setMap(ArrayList<GridElement> data){
        GridGameScreen.currentMap = data;
    }

    public static void setSolverBFS(boolean solverBFS) {
        GridGameScreen.solverBFS = solverBFS;
    }

    @Override
    public void create()
    {
        gmi = new GameMovementInterface();

        xGrid = 0;
        yGrid = 1080/5;

        int visibleScreenHeight=gameManager.getScreenHeight(); // bei 720x1280:1184px

        int y = yGrid+gameManager.getScreenWidth();
        int dy = visibleScreenHeight-y; // 248
        int buttonW = gameManager.getScreenWidth()/4;

        float ratioW = ((float)gameManager.getScreenWidth()) /((float)1080); // bei 720x1280:0.6667 bei 1440x2580:1.333
        float ratioH = ((float)visibleScreenHeight) /((float)1920); // bei 720x1280:0.61667 bei 1440x2580:2.45
        //int buttonPosY = (int)(6.5*dy * ratioH);
        int buttonPosY = y+10*dy/20; // 1060
        int nextButtonDim=(int)(160*ratioH);

        if(visibleScreenHeight<=1280){
            // on very low res screens
            nextButtonDim=(int)(220*ratioH);
            buttonPosY = -50 +y+10*dy/20;
        }

        // Button Next game (top right)
        this.instances.add(new GameButtonGeneral((int)(870*ratioW), (int)(0*ratioH), nextButtonDim, nextButtonDim, R.drawable.bt_next_up, R.drawable.bt_next_down, new ButtonNext()));

        // Button Save
        gameManager.getRenderManager().loadImage(R.drawable.transparent);
        buttonSave = new GameButtonGoto(0, buttonPosY, buttonW,buttonW, R.drawable.bt_jeu_save_up, R.drawable.bt_jeu_save_down, 9);
        buttonSave.setImageDisabled(R.drawable.transparent);
        // save button will be disabled when playing a saved game
        buttonSave.setEnabled(true);
        this.instances.add(buttonSave);

        // Button one step back
        this.instances.add(new GameButtonGeneral(buttonW, buttonPosY, buttonW,buttonW, R.drawable.bt_jeu_retour_up, R.drawable.bt_jeu_retour_down, new ButtonBack()));

        // Button restart
        this.instances.add(new GameButtonGeneral(buttonW*2, buttonPosY, buttonW,buttonW, R.drawable.bt_jeu_reset_up, R.drawable.bt_jeu_reset_down, new ButtonRestart()));

        // Button Solve
        gameManager.getRenderManager().loadImage(R.drawable.bt_jeu_resolution_disabled);
        buttonSolve = new GameButtonGeneral(buttonW*3, buttonPosY, buttonW,buttonW, R.drawable.bt_jeu_resolution_up, R.drawable.bt_jeu_resolution_down, new ButtonSolution());
        buttonSolve.setImageDisabled(R.drawable.bt_jeu_resolution_disabled);
        buttonSolve.setEnabled(false);
        this.instances.add(buttonSolve);

        this.solver = new SolverDD();
    }

    /**
     * possibility to disable the saveButton from outside in GameButtonGotoSavedGame.java
     * @param status set false to disable
     */
    public void buttonSaveSetEnabled(boolean status){
        buttonSave.setEnabled(status);
    }

    @Override
    public void load(RenderManager renderManager) {
        super.load(renderManager);
        this.gmi.load(renderManager);
    }

    @Override
    public void draw(RenderManager renderManager)
    {
        //renderManager.setColor(Color.argb(255, 255, 228, 0));
        renderManager.setColor(Color.BLACK);
        // ffe400
        // ff7c24
        renderManager.paintScreen();

        //renderManager.setColor(Color.BLACK);
        renderManager.setColor(Color.GRAY);
        int lineHeight = yGrid/2-60; // 58
        int lineHeightSmall = lineHeight-15;
        int textPosY = lineHeight;
        int textPosYSmall = 2*lineHeight-4;
        int textPosYTime = yGrid/2+lineHeight;
        renderManager.setTextSize(lineHeight);

        if(nbCoups>0){
            // at least one move was made by hand or by AI
            renderManager.drawText(10, textPosY, "Number of moves: " + nbCoups);
        } else if(isSolved && numSolutionClicks>0){
            // show solution
            if(numSolutionClicks-showSolutionAtHint >= 0) {
                renderManager.drawText(10, textPosY, "AI solution: " + solutionMoves + " moves");
            } else {
                renderManager.drawText(10, textPosY, "AI Hint " + numSolutionClicks + ": < " + (solutionMoves+showSolutionAtHint-numSolutionClicks) + " moves");
            }
        } else if(nbCoups==0 && isSolved && solutionMoves < simplePuzzleMinMoves){
            // too simple ... restart
            renderManager.drawText(10, textPosY, "AI solution: " + solutionMoves + " moves");
            renderManager.setTextSize(lineHeightSmall);
            renderManager.drawText(10, textPosYSmall, "... restarting!");
            if(timeCpt>2 || solutionMoves>14){
                // show a popup on restart if it took very long or on very big puzzles
                requestToast = "Solved in " + solutionMoves + " moves " + (solverBFS?"with BFS (which is slower than IDDFS. this can be changed in Settings)":"") + ". Restarting...";
            }
            mustStartNext = true;
        } else if(nbCoups==0 && isSolved && solutionMoves < goodPuzzleMinMoves){
            // still simple, show a hint that this is solved with less than ... moves
            // TODO: change font (still crashes):
            //  renderManager.drawText(10, textPosY, "Number of moves < " + goodPuzzleMinMoves, "FiraMono-Bold", gameManager.getActivity());
            renderManager.drawText(10, textPosY, "Number of moves < " + goodPuzzleMinMoves);
            showSolutionAtHint = goodPuzzleMinMoves - solutionMoves;
        } else if(!isSolved){
            renderManager.drawText(10, textPosY, "AI solving with " + (solverBFS?"BFS (slower)":"IDDFS") + "...");
        }
        int seconds = timeCpt%60;
        String secondsS = Integer.toString(seconds);
        if(seconds < 10){
            secondsS="0" + secondsS;
        }
        renderManager.drawText(10, textPosYTime, "Time: " + timeCpt / 60 + ":" + secondsS);

        if(timeCpt>=40 && autoSaved == false){
            // save autosave in slot 0
            ArrayList gridElements = getGridElements();
            String autosaveMapPath=SaveGameScreen.getMapPath(0);
            FileReadWrite.clearPrivateData(gameManager.getActivity(), autosaveMapPath);
            FileReadWrite.writePrivateData((gameManager.getActivity()), autosaveMapPath, MapObjects.createStringFromList(gridElements));
            gameManager.requestToast("Autosaving...", true);
            autoSaved = true;
        }

        if(imageLoaded)
        {
            gameManager.getRenderManager().drawImage(xGrid, yGrid, (int)(16*gridSpace) + xGrid, (int)(16*gridSpace) + yGrid,  imageGridID);
        }
        super.draw(renderManager);
        this.gmi.draw(renderManager);

        if(requestToast!=""){
            // show double toast to last longer
            gameManager.requestToast(requestToast, true);
            gameManager.requestToast(requestToast, true);
            requestToast="";
        }
    }

    public void update(GameManager gameManager){
        super.update(gameManager);

        if(mustStartNext) {

            numSolutionClicks = 0;

            // show solution as the 3rd to 5th hint
            showSolutionAtHint = 3 + (int)(Math.random() * ((5 - 3) + 1));

            allMoves.clear();
            autoSaved = false;

            buttonSave.setEnabled(true);

            buttonSolve.setEnabled(false);
            if(t != null){
                t.interrupt();
                moves = null;
                t = null;
            }
            int integer = -1;

            if(!mapPath.equals(""))
            {

//                int value = 0;
//                Scanner s = new Scanner(mapPath);

                Scanner in = new Scanner(mapPath).useDelimiter("[^0-9]+");
                integer = in.nextInt();

                //value = s.nextInt();
                System.out.println("Value mappath:"+integer);
            }

            System.out.println("B");
            if(integer >=0 && integer < 60)
            {
                mapPath = "Maps/generatedMap_"+(integer+1)+".txt";
                setLevelGame(mapPath);
            }
            else
            {
                setRandomGame(true);
            }
            System.out.println("C");

            mustStartNext = false;
        }
        if(System.currentTimeMillis() - prevTime > 1000L){
            timeCpt++;
            prevTime = System.currentTimeMillis();
        }
        this.gmi.update(gameManager);
        if(gameManager.getInputManager().backOccurred()){
            if(t != null){
                t.interrupt();
                moves = null;
                t = null;
            }
            gameManager.setGameScreen(1);
        }

        if(!isSolved && solver.getSolverStatus().isFinished())
        {
            isSolved = true;
            buttonSolve.setEnabled(true);
            GameSolution solution = solver.getSolution();
            solutionMoves=0;
            for(IGameMove m : solution.getMoves()){
                solutionMoves++;
            }
            /*if(solutionMoves > simplePuzzleMinMoves && solutionMoves < goodPuzzleMinMoves) {
                // very simple puzzle with max 6 moves
                gameManager.requestToast("AI sais: this is a simple puzzle.", true);
            }*/
        }
    }

    @Override
    public void destroy(){
        if(t != null){
            t.interrupt();
            moves = null;
            t = null;
        }
    }

    public ArrayList getGridElements() {
        return gridElements;
    }

    public void setSavedGame(String mapPath)
    {
        this.mapPath = "";

        gridElements = MapObjects.extractDataFromString(FileReadWrite.readPrivateData(gameManager.getActivity(), mapPath));
        GridGameScreen.setMap(gridElements);

        createGrid();
    }

    public void setLevelGame(String mapPath)
    {
        this.mapPath = mapPath;
        //setGame(mapPath);

        System.out.println("SetLevelGame");
        gridElements = MapObjects.extractDataFromString(FileReadWrite.readAssets(gameManager.getActivity(), mapPath));
        System.out.println("SetLevelGame, gridElements :"+gridElements.size());
        // TODO: this doesn't work here:
        //  GridGameScreen.setMap(gridElements);

        // this.solver = new SolverRR();

        createGrid();
    }

    public void setRandomGame(boolean random)
    {

        this.mapPath = "";  //La carte étant générée, elle n'a pas de chemin d'accès
        MapGenerator generatedMap = new MapGenerator();
        gridElements = generatedMap.getGeneratedGameMap();

        // this.solver = new SolverDD();

        createGrid();
    }

    public void createGrid()
    {
        if(solverBFS) {
            // set to BFS (slower)
            this.solver = new SolverRR();
        } else {
            // IDFSS
            this.solver = new SolverDD();
        }
        IAMovesNumber = 0;
        isSolved = false;

        nbCoups = 0;
        timeCpt = 0;
        prevTime = System.currentTimeMillis();

        currentRenderManager.setTarget(canvasGrid);

        drawables.put("grid", currentRenderManager.getResources().getDrawable(R.drawable.grid)); // white background
        drawables.put("mh", currentRenderManager.getResources().getDrawable(R.drawable.mh)); // horizontal lines
        drawables.put("mv", currentRenderManager.getResources().getDrawable(R.drawable.mv)); // vertical lines

        drawables.put("rv", currentRenderManager.getResources().getDrawable(R.drawable.rv)); // green robot
        drawables.put("rr", currentRenderManager.getResources().getDrawable(R.drawable.rr)); // red
        drawables.put("rj", currentRenderManager.getResources().getDrawable(R.drawable.rj)); // yellow
        drawables.put("rb", currentRenderManager.getResources().getDrawable(R.drawable.rb)); // blue

        drawables.put("cv", currentRenderManager.getResources().getDrawable(R.drawable.cv)); // green goal
        drawables.put("cr", currentRenderManager.getResources().getDrawable(R.drawable.cr)); // ...
        drawables.put("cj", currentRenderManager.getResources().getDrawable(R.drawable.cj)); //
        drawables.put("cb", currentRenderManager.getResources().getDrawable(R.drawable.cb)); //
        drawables.put("cm", currentRenderManager.getResources().getDrawable(R.drawable.cm)); // multicolor goal


        // white background of grid
        drawables.get("grid").setBounds(0, 0,(int)( 16 * gridSpace),(int)( 16 * gridSpace));
        drawables.get("grid").draw(canvasGrid);

        // draw targets
        for (Object element : gridElements) {
            GridElement myp = (GridElement) element;

            if (myp.getType().equals("cr") || myp.getType().equals("cv") || myp.getType().equals("cj") || myp.getType().equals("cb") || myp.getType().equals("cm")) {
                drawables.get(myp.getType()).setBounds((int)(myp.getX() * gridSpace),(int)( myp.getY() * gridSpace),(int)( (myp.getX() + 1) * gridSpace),(int)( (myp.getY()+1) * gridSpace));
                drawables.get(myp.getType()).draw(canvasGrid);
            }
        }

        // draw horizontal lines
        for (Object element : gridElements) {
            GridElement myp = (GridElement) element;

            if (myp.getType().equals("mh")) {
                drawables.get("mh").setBounds((int)(myp.getX() * gridSpace), (int)(myp.getY() * gridSpace -2), (int)((myp.getX() + 1) * gridSpace), (int)(myp.getY() * gridSpace + gridLineThickness));
                drawables.get("mh").setColorFilter(wallColor);
                drawables.get("mh").draw(canvasGrid);
            }

            if (myp.getType().equals("mv")) {
                // vertical lines
                drawables.get("mv").setBounds((int)(myp.getX() * gridSpace - 2), (int)(myp.getY() * gridSpace), (int)(myp.getX() * gridSpace + gridLineThickness), (int)((myp.getY() + 1) * gridSpace));
                drawables.get("mv").setColorFilter(wallColor);
                drawables.get("mv").draw(canvasGrid);
            }
        }

        currentRenderManager.resetTarget();

        //On supprime l'image de fond si elle existe et on sauvegarde celle que l'on vient de créer
        if(imageLoaded == true)
        {
            currentRenderManager.unloadBitmap(imageGridID);
        }
        imageGridID = currentRenderManager.loadBitmap(bitmapGrid);
        imageLoaded = true;


        createRobots();


        this.solver.init(gridElements);

        buttonSolve.setEnabled(false);
        t = new Thread(solver, "solver");
        t.start();

    }

    public void createRobots()
    {
        colors.put("rr", Color.RED);
        colors.put("rb", Color.BLUE);
        colors.put("rv", Color.GREEN);
        colors.put("rj", Color.YELLOW);
        
        colors.put("cr", Color.RED);
        colors.put("cb", Color.BLUE);
        colors.put("cv", Color.GREEN);
        colors.put("cj", Color.YELLOW);

        ArrayList<GamePiece> aRemove = new ArrayList<>();
        for(Object currentObject : this.instances)
        {
            if(currentObject.getClass() == GamePiece.class)
            {
                aRemove.add((GamePiece)currentObject);
            }
        }
        for(GamePiece p : aRemove)
        {
            this.instances.remove(p);
        }

        for (Object element : gridElements) {
            GridElement myp = (GridElement) element;

            if (myp.getType().equals("rr") || myp.getType().equals("rv") || myp.getType().equals("rj") || myp.getType().equals("rb")) {

                GamePiece currentPiece = new GamePiece(myp.getX(), myp.getY(), colors.get(myp.getType()));
                currentPiece.setGridDimensions(xGrid, yGrid, gridSpace);

                this.instances.add(currentPiece);
            }
        }

    }

    public void activateInterface(GamePiece p, int x, int y){
        gmi.enable(true);
        gmi.setPosition(x-1, y);
        gmi.setTarget(p);
    }

    public void editDestination(GamePiece p, int direction, Boolean moved)
    {
        int xDestination = p.getxObjective();
        int yDestination = p.getyObjective();

        boolean canMove = true;

        if(!moved) {
            Move currentMove = new Move(p, p.getxObjective(), p.getyObjective());
            allMoves.add(currentMove);
        }

        for(Object instance : this.instances)
        {
            if(instance.getClass() == p.getClass() && p != instance && canMove)
            {
                switch(direction){
                    case 0:     // haut
                        canMove = collision((GamePiece) instance, xDestination, yDestination - 1, canMove);
                        break;
                    case 1:     // droite
                        canMove = collision((GamePiece) instance, xDestination+1, yDestination, canMove);
                        break;
                    case 2:     // bas
                        canMove = collision((GamePiece) instance, xDestination, yDestination + 1, canMove);
                        break;
                    case 3:     // gauche
                        canMove = collision((GamePiece) instance, xDestination-1, yDestination, canMove);
                        break;
                }
            }
        }

        for (Object element : gridElements) {
            GridElement myp = (GridElement) element;

            if ((myp.getType().equals("mv")) && (direction == 1)) {  // droite
                canMove = collision(p, myp.getX() - 1, myp.getY(), canMove);
            }
            if ((myp.getType().equals("mv")) && (direction == 3)) {  // gauche
                canMove = collision(p, myp.getX(), myp.getY(), canMove);
            }
            if ((myp.getType().equals("mh")) && (direction == 0)) {  // haut
                canMove = collision(p, myp.getX(), myp.getY(), canMove);
            }
            if ((myp.getType().equals("mh")) && (direction == 2)) {  // bas
                canMove = collision(p, myp.getX(), myp.getY() - 1, canMove);
            }
        }

        if(canMove)
        {
            switch(direction){
                case 0:     // haut
                    yDestination -= 1;
                    break;
                case 1:     // droite
                    xDestination +=1;
                    break;
                case 2:     // bas
                    yDestination += 1;
                    break;
                case 3:     // gauche
                    xDestination -=1;
                    break;
            }
            p.setxObjective(xDestination);
            p.setyObjective(yDestination);

            editDestination(p, direction, true);
        }else{
            if(moved)
            {
                nbCoups++;
                //boolean b = gagne(p);

            }
            else
            {
                allMoves.remove(allMoves.size()-1);

            }
        }
    }

    public boolean gagne(GamePiece p)
    {
        for (Object element : gridElements) {
            GridElement myp = (GridElement) element;
            {
                 if (myp.getType().equals("cm") && myp.getX() == p.getX() && myp.getY() == p.getY())
                {
                    sayWon();

                    return true;
                }
                else if((myp.getX() == p.getX()) && (myp.getY() == p.getY()) && (myp.getType().equals("cr") || myp.getType().equals("cv") || myp.getType().equals("cb") || myp.getType().equals("cj")))
                {
                    if(p.getColor() == colors.get((myp.getType())))
                    {
                        sayWon();

                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void sayWon()
    {
        if(IAMovesNumber > 0)
        {
            gameManager.requestToast("The AI found a solution in "+IAMovesNumber+" moves.", true);
        }
        else
        {
            gameManager.requestToast("You won in "+nbCoups+" moves.", true);
        }
        updatePlayedMaps();
    }

    private void updatePlayedMaps()
    {
        if(mapPath.length() > 0) {
            addMapsPlayed();
            SparseArray<GameScreen> screens = gameManager.getScreens();
            LevelChoiceGameScreen.getLastButtonUsed().setImageUp(R.drawable.bt_start_up_played);
            LevelChoiceGameScreen.getLastButtonUsed().setImageDown(R.drawable.bt_start_down_played);
            /*
            for (int i = 0; i < screens.size(); i++) {
                if (screens.get(i).getClass() == LevelChoiceGameScreen.class) {
                    ((LevelChoiceGameScreen) screens.get(i)).createButtons();
                }
            }*/
        }
    }


    public void addMapsPlayed()
    {
        if(mapPath.length() > 0)
        {
            SaveManager saver = new SaveManager(gameManager.getActivity());

            if(!saver.getMapsStateSaved(mapPath, "mapsPlayed.txt"))
            {
                FileReadWrite.writePrivateData(gameManager.getActivity(), "mapsPlayed.txt", mapPath.substring(5)+"\n");
            }
        }
    }

    public Boolean collision(GamePiece p, int x, int y, boolean canMove)
    {
        if(p.getxObjective() == x && p.getyObjective() == y && canMove == true)
            return false;
        else if(canMove == false)
            return false;
        return true;
    }

    private class ButtonRestart implements IExecutor{

        public void execute(){
            ButtonBack bb = new ButtonBack();
            while(allMoves.size()>0)
            {
                bb.execute();
            }
            nbCoups = 0;
        }
    }

    private class ButtonNext implements IExecutor{

        public void execute(){
            mustStartNext = true;

        }
    }

    private class ButtonSolution implements IExecutor{
        public void execute(){
            if(numSolutionClicks >= showSolutionAtHint) {
                GameSolution solution = solver.getSolution();
                showSolution(solution);
            }else{
                numSolutionClicks++;
                if(numSolutionClicks < showSolutionAtHint) {
                    gameManager.requestToast("Press again to see the next hint.", true);
                } else {
                    gameManager.requestToast("Press again to see the solution.", true);
                }
            }
        }
    }

    public void doMovesInMemory()
    {

        if(moves != null)
        {

            if(moves.size() > 0)
            {

                IGameMove move = moves.get(0);

                if(move.getClass() == RRGameMove.class)
                {

                    for (Object currentObject : this.instances)
                    {
                        if(currentObject.getClass() == GamePiece.class)
                        {
                            if(((GamePiece)currentObject).getColor() == ((RRGameMove) move).getColor())
                            {
                                editDestination(((GamePiece) currentObject), translateIADirectionToGameDirection(((RRGameMove) move).getDirection()), false);
                            }
                        }
                    }
                }
                moves.remove(0);
            }
        }
    }

    private void showSolution(GameSolution solution)
    {
        ButtonRestart br = new ButtonRestart();
        br.execute();

        moves = solution.getMoves();
        IAMovesNumber = moves.size();

        doMovesInMemory();
    }

    private int translateIADirectionToGameDirection(int IADirection)
    {
        switch(IADirection){
            case 1:
                return 0;
            case 2:
                return 1;
            case 4:
                return 2;
            case 8:
                return 3;
            default:
                return -1;
        }
    }

    private class ButtonBack implements IExecutor{
        public void execute(){
            if(allMoves.size() > 0)
            {
                allMoves.get(allMoves.size()-1).goBack();

                allMoves.remove(allMoves.size()-1);
                nbCoups--;
            }
        }
    }
}
