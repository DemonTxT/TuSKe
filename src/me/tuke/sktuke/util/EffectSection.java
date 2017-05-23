package me.tuke.sktuke.util;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.*;
import ch.njol.skript.log.*;
import ch.njol.util.Kleenean;
import ch.njol.util.StringUtils;
import com.sun.javafx.event.RedirectedEvent;
import me.tuke.sktuke.TuSKe;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * A class to allow you to create effects that you can run its section.
 * @author Tuke_Nuke on 29/03/2017
 */
public abstract class EffectSection extends Condition {
	private SectionNode section = null;
	private TriggerSection trigger = null;
	private boolean hasIfOrElseIf = false;

	public EffectSection(){
		Node n = SkriptLogger.getNode(); //Skript sets the node before parsing this 'effect'
		if (n!= null && n instanceof SectionNode) { //Check in case it wasn't loaded as inline condition
			//True if it was used as condition
			hasIfOrElseIf = StringUtils.startsWithIgnoreCase(n.getKey(), "if ") || StringUtils.startsWithIgnoreCase(n.getKey(), "else if ");
			//The comment value of a note is protected, so it is needed but not really necessary tho.
			//It doesn't make difference, it's just to make a exactly copy.
			String comment = ReflectionUtils.getField(Node.class, n, "comment");
			if (comment == null)
				comment = "";
			//Creating a copy of current node.
			section = new SectionNode(n.getKey(), comment, n.getParent(), n.getLine());
			//It will copy the "ArrayList<Node> nodes" field too as it is protected.
			ReflectionUtils.setField(SectionNode.class, section, "nodes", ReflectionUtils.getField(SectionNode.class, n, "nodes"));
			//Then it will clear the nodes from the current node, so Skript won't parse it (you need to parse then later).
			ReflectionUtils.setField(SectionNode.class, n, "nodes", new ArrayList<Node>());
		}
	}
	/**
	 * It is to replicate {@link ch.njol.skript.lang.Effect#execute(Event)}
	 * @param e - The Event
	 */
	public abstract void execute(Event e);
	@Override
	public boolean check(Event e){
		execute(e);
		//It needs to return false to not enter inside the section
		//And return true in case it is inline condition, so the code
		//can continue.
		return !hasSection();
	}

	/**
	 * It will load the section of this if any. It must be used before {@link #runSection(Event)}.
	 */
	public void loadSection(){
		if (section != null) {
			//Some how, there is a RetainingLogHandler not logging the errors from a section, it will stop them before.


			RetainingLogHandler errors = SkriptLogger.startRetainingLog();
			try {
				trigger = new TriggerSection(section) {

					@Override
					public String toString(Event event, boolean b) {
						return EffectSection.this.toString(event, b);
					}

					@Override
					public TriggerItem walk(Event event) {
						return walk(event, true);
					}
				};
			} finally {
				stopLog(errors);
			//	errors.printLog();
			//	TuSKe.debug("Logando...");
			//	SkriptLogger.LOGGER.severe("AAAAAA");
			//	for(LogEntry log : errors.getLog()) {
			//		SkriptLogger.logTracked(log.getLevel(), log.getMessage(), ErrorQuality.get(log.getQuality()));
			//	}

			}
			//Just to not keep a instance of SectionNode.
			section = null;
		}
	}

	/**
	 * It will load the section of this if any and then it will parse as in specific event.
	 * Basically it will call {@link ScriptLoader#setCurrentEvent(String, Class[])}, parse the current section,
	 * and then set the current event back to the previous one.
	 * Useful to load a code from event X and parse as Y, allowing to use syntaxes that work on it.
	 *
	 * @param name - The name of event (It can be anything)
	 * @param events - The classes that extends {@link Event}.
	 */
	public void loadSection(String name, Class<? extends Event>... events){
		if (section != null && name != null && events != null && events.length > 0) {
			String previousName = ScriptLoader.getCurrentEventName();
			Class<? extends Event>[] previousEvents = ScriptLoader.getCurrentEvents();
			Kleenean previousDelay = ScriptLoader.hasDelayBefore;
			ScriptLoader.setCurrentEvent(name, events);
			loadSection();
			ScriptLoader.setCurrentEvent(previousName, previousEvents);
			ScriptLoader.hasDelayBefore = previousDelay;
		}
	}

	/**
	 * Check if this has any section (basically check if it is inline condition or Condtional)
	 * @return True if it has
	 */
	public boolean hasSection(){
		return section != null || trigger != null;
	}

	/**
	 * Run the section.
	 * <b>Note</b>: You must {@link #loadSection()} first and you should run it with same
	 * event from {@link #execute(Event)}
	 * @param e - The event
	 */
	public void runSection(Event e){
		TriggerItem.walk(trigger, e);
	}

	/**
	 * It will check in case the effect wasn't used with 'if/else if' before
	 * <code>
	 *     do something:
	 *     	send "Everything fine"
	 *     if do something:
	 *     	send "Not ok, it will send a default message and return false"
	 * </code>
	 * It needs to be used in {@link #init(Expression[], int, Kleenean, SkriptParser.ParseResult)}
	 * method, like:
	 * <code>
	 *     public boolean init(...) {
	 *		if (checkIfCondition()) { //It will send a error if true
	 *		 return false; //Then return false to not continue the code
	 *		}
	 *		//continue here
	 *     }
	 * </code>
	 * @return True if the EffectSection was used as condition in if/else if
	 */
	public boolean checkIfCondition() {
		if (hasIfOrElseIf)
			Skript.error("You can't use the effect in if/else if section.");
		return hasIfOrElseIf;
	}

	/**
	 * The section node of {@link EffectSection}.
	 * It can return null if it was used after {@link #loadSection()} or
	 * if this doesn't have any section.
	 * @return The SectionNode
	 */
	public SectionNode getSectionNode() {
		return section;
	}

	/**
	 *
	 * @param logger
	 */
	private void stopLog(RetainingLogHandler logger) {

		logger.stop();
		HandlerList handler = ReflectionUtils.getField(SkriptLogger.class, null, "handlers");
		Iterator<LogHandler> it = handler.iterator();
		LogHandler main = null;
		TuSKe.debug("Log 1");
		int x = 0;
		while (it.hasNext()) {
			LogHandler log = it.next();
			//printErrors(log);
			log.log(new LogEntry(Level.INFO, "EEEEEE: " + x++ + log.getClass()));
			//TuSKe.debug(log.getClass());
			if (log instanceof RedirectingLogHandler) {
				//TuSKe.debug(log, main);
				main = log;
				break;
			} else if (log instanceof RetainingLogHandler || log instanceof ParseLogHandler) {
				main = log;
				//TuSKe.debug("else", main);
			}
		}
		TuSKe.debug("Log 2");
		//TuSKe.debug("Before: " + logger.getLog().size(), (main != null ? main.numErrors() : 0));
		if (main != null)
			for (LogEntry log : logger.getLog())
				main.log(log);
		//TuSKe.debug("After: " + logger.getLog().size(), (main != null ? main.numErrors() : 0));
	}
}
