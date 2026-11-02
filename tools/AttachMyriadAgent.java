import com.sun.tools.attach.VirtualMachine;

public final class AttachMyriadAgent {
    private AttachMyriadAgent() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: AttachMyriadAgent <pid> <agent-jar>");
        }

        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            vm.loadAgent(args[1]);
            System.out.println("Myriad agent attached to process " + args[0]);
        } finally {
            vm.detach();
        }
    }
}
