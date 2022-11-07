package de.davidrival.softstep.controller;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.ControllerHost;
import de.davidrival.softstep.api.ApiManager;
import de.davidrival.softstep.api.SimpleConsolePrinter;
import de.davidrival.softstep.hardware.SoftstepHardware;

import de.davidrival.softstep.hardware.SoftstepHardwareBase;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Getter
@Setter
public class SoftstepController extends SimpleConsolePrinter {

    public static final int NAV_PAD_PUSHED_DOWN_TRESHOLD = 2;

    private SoftstepHardware softstepHardware;

    final Controlls controls;

    private ApiManager apiManager;

    private ControllerHost controllerHost;

    private ControllerPages pages;

    private List<HasControllsForPage> hasControllsForPages;

    public SoftstepController(
            ControllerPages controllerPages
            , SoftstepHardware softstepHardware
            , ApiManager apiManager
            , ControllerHost hostOrNull) {

        super(hostOrNull);
        this.pages = controllerPages;
        this.softstepHardware = softstepHardware;
        this.apiManager = apiManager;
        this.apiManager.setController(this);
        this.controls = new Controlls(apiManager.getHost());

        hasControllsForPages = new ArrayList<>();
        HasControllsForPage clipControlls = new ClipControlls(Page.CLIP, apiManager);
        HasControllsForPage userControlls = new UserControlls(Page.CTRL, apiManager);
        hasControllsForPages.add(clipControlls);
        hasControllsForPages.add(userControlls);

    }

    public void display() {
        softstepHardware.displayText(pages.getCurrentPage().name());
        softstepHardware.showAllLeds(pages.getCurrentPage());
    }

    public void handleMidi(ShortMidiMessage msg) {

        // don't forward midi if consumed for page change
        if (isMidiUsedForPageChange(msg)) return;

        controls.update(msg);

        triggerBitwigIfControlsUsed(controls);
    }

    private void triggerBitwigIfControlsUsed(Controlls controls) {
        List<Softstep1Pad> pushedDownPads = controls.getPads()
                .stream()
                .filter(pad -> pad.isUsed())
                .collect(Collectors.toList());

//        If no controlls where used on the device just exit
        if (pushedDownPads.size() == 0) return;

            hasControllsForPages.stream()
                    .filter(c -> c.getPage().equals(pages.getCurrentPage()))
                    .findFirst().ifPresent(
                            p -> p.processControlls(pushedDownPads)
                    );
    }

    private boolean isMidiUsedForPageChange(ShortMidiMessage msg) {
        if (msg.getStatusByte() == SoftstepHardwareBase.STATUS_BYTE
                && msg.getData2() > NAV_PAD_PUSHED_DOWN_TRESHOLD) {

            if (msg.getData1() == SoftstepHardwareBase.NAV_LEFT_DATA1) {
                if (pages.getCurrentPage().pageIndex != Page.CLIP.pageIndex) {
                    pages.setCurrentPage(Page.CLIP);
                    display();
                }
                return true;
            }
            if (msg.getData1() == SoftstepHardwareBase.NAV_RIGHT_DATA1) {
                if (pages.getCurrentPage().pageIndex != Page.CTRL.pageIndex) {
                    pages.setCurrentPage(Page.CTRL);
                    display();
                }
                return true;
            }
        }
        return false;
    }


    /**
     * Write to the states of each page
     * Doesn't matter is currently active or not
     * Needed if one wants to switch to another mode. Afterwards it sends out the
     * changed led states to the control for the acive page
     *
     * @param page      the page where the LED is to be set (not importand if active or not
     * @param index     the index of the controller ( e.g. the slotindex if used in clip mode)
     * @param ledStates the led states, meaninf color and flashin mode
     */
    public void updateLedStates(Page page, int index, LedStates ledStates) {
        // First write to the states of each page
        // Doesn't matter is currently active or not
        // Needed if one wants to switch to another mode
        pages.distributeLedStates(page, index, ledStates);

//        p(" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>> ");
//        p(Page.CLIP.ledStates.toString());
//        p(" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>> ");

        // Only render the led states of the active page
        if (pages.getCurrentPage().equals(page)) {
            softstepHardware.drawLedAt(index, ledStates);
        }
    }

    public void exit() {
        softstepHardware.exit();
    }

}
