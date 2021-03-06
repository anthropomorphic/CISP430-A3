package com.mdorst;

import com.mdorst.container.hash.HashMap;
import com.mdorst.container.list.Iterator;
import com.mdorst.container.list.LinkedList;
import com.mdorst.container.list.Queue;
import com.mdorst.container.list.Stack;
import com.mdorst.exception.SyntaxError;
import com.mdorst.exception.UnrecognizedSymbolError;

import java.util.StringTokenizer;

public class Interpreter {

    /**
     * Maps variable names to values.
     */
    private HashMap<String, Double> symbols = new HashMap<>(String::hashCode);

    /**
     * Takes an expression, interprets it, and returns the result.
     * @param expression The expression to be interpreted
     * @return The result of the expression
     */
    public double interpret(String expression) throws SyntaxError {
        LinkedList<String> expr = new LinkedList<>();
        /**
         * Tokenize the expression
         */
        {
            LinkedList<String> tokenList = new LinkedList<>();
            StringTokenizer tokenizer = new StringTokenizer(expression, "+-*/=() \n\t\r\f", true);
            while (tokenizer.hasMoreTokens()) {
                tokenList.add(tokenizer.nextToken());
            }
            /**
             * Remove whitespace tokens
             */
            tokenList.removeAll(" ");
            tokenList.removeAll("\t");
            tokenList.removeAll("\n");
            tokenList.removeAll("\r");
            tokenList.removeAll("\f");
            /**
             * Add "+" before "-" anywhere where "-" is not a unary operator
             */
            Iterator<String> i = tokenList.iterator();
            String previous = "a";
            while (i.hasNext()) {
                String token = i.next();
                if (token.equals("-") && !previous.matches("[\\+\\*/=]|\\(|-|sin|cos|tan|cot|sec|csc|abs|sqrt")) {
                    expr.add("+");
                }
                expr.add(token);
                previous = token;
            }
        }
        /**
         * Create postfix expression
         */
        Stack<String> opStack = new Stack<>();
        Queue<Token> postfix = new Queue<>();
        for (String token : expr) {
            /**
             * Unary operators:
             * Always pushed to the opStack (highest priority)
             */
            if (token.matches("-|sin|cos|tan|cot|sec|csc|abs|sqrt")) {
                opStack.push(token);
            }
            /**
             * Variables:
             * Always get pushed to the postfix expression
             */
            else if (token.matches("([a-z]|[A-Z]|_)\\w*")) {
                postfix.enqueue(new Token(token));
            }
            /**
             * Numeric literals:
             * Always get pushed to the postfix expression
             */
            else if (token.matches("\\d+")) {
                postfix.enqueue(new Token(Double.valueOf(token)));
            }
            /**
             * Binary operators:
             */
            else if (token.equals("=")) {
                /**
                 * BinaryOperator =
                 * Only valid if it's the first operator found.
                 * Otherwise it's an error (there cannot be operators
                 * on the left hand side of an assignment).
                 */
                if (opStack.isEmpty()) {
                    opStack.push("=");
                } else {
                    String e = expression.substring(0, expression.indexOf("="));
                    throw new SyntaxError("Cannot assign to the expression " + e);
                }
            }
            /**
             * Operator +
             *
             */
            else if (token.matches("\\+")) {
                while (!opStack.isEmpty()) {
                    if (opStack.top().matches("-|\\+|\\*|/"))
                        postfix.enqueue(new Token(opStack.pop()));
                    else break;
                }
                opStack.push(token);
            }
            else if (token.matches("\\*|/")) {
                while (!opStack.isEmpty()) {
                    if (opStack.top().matches("-|\\*|/"))
                        postfix.enqueue(new Token(opStack.pop()));
                    else break;
                }
                opStack.push(token);
            }
            else if (token.equals("(")) {
                opStack.push(token);
            }
            else if (token.equals(")")) {
                if (opStack.isEmpty() || opStack.top().equals("=")) {
                    throw new SyntaxError("Unmatched ')'");
                }
                /**
                 * Unstack until "(" is found
                 */
                while (!opStack.isEmpty() && !opStack.top().equals("(")) {
                    postfix.enqueue(new Token(opStack.pop()));
                }
                if (!opStack.pop().equals("(")) {
                    throw new SyntaxError("Unmatched ')'");
                }
            }
            /**
             * Anything else
             */
            else {
                /**
                 * Unrecognized symbol (did not match any recognized pattern)
                 * An exception will be thrown.
                 */
                throw new UnrecognizedSymbolError(token + " is not a valid token");
            }
        }
        /**
         * If there are any operators left on the operator stack,
         * enqueue them onto postfix.
         */
        while (!opStack.isEmpty()) {
            postfix.enqueue(new Token(opStack.pop()));
        }
        /**
         * Evaluate postfix expression
         */
        Stack<Token> eval = new Stack<>();
        while (!postfix.isEmpty()) {
            /**
             * If a token is not an operator, then it is either a variable or a numeric literal.
             * Variables and numeric literals are automatically pushed to eval stack.
             */
            if (!postfix.front().isOperator()) {
                eval.push(postfix.dequeue());
                /**
                 * If a variable was pushed, look up its value and convert it to
                 * a value token UNLESS it is the only token on the eval stack, (which means it is the
                 * variable being assigned to, and needs to remain a variable).
                 */
                if (eval.size() > 1 && eval.top().isVariable()) {
                    Double value = symbols.get(eval.pop().name());
                    /**
                     * If value is null, the variable was not found in the symbol table
                     */
                    if (value == null) {
                        throw new UnrecognizedSymbolError("Error: Variables cannot be used before they are assigned");
                    }
                    eval.push(new Token(value));
                }
            }
            else if (postfix.front().isUnaryOperator()) {
                /**
                 * When a unary operator is reached, one value is popped from the eval stack.
                 * The operator is applied to it, and the result is pushed onto the eval stack.
                 *
                 * If there isn't at least one operand to operate on, there's an error.
                 */
                if (eval.size() < 1) {
                    throw new SyntaxError("Expected an operand for operator " + postfix.dequeue().name());
                }
                UnaryOperator operator = getUnaryOperator(postfix.dequeue().name());
                Token operand = eval.pop();
                double result = operator.call(operand);
                eval.push(new Token(result));
            }
            else {
                /**
                 * When a binary operator is reached, two values are popped from the eval stack.
                 * The operator is applied to them, and the result is pushed onto the eval stack.
                 *
                 * If there aren't at least two operands to operate on, there's an error.
                 */
                if (eval.size() < 2) {
                    throw new SyntaxError("Expected two operands for operator " + postfix.dequeue().name());
                }
                BinaryOperator operator = getBinaryOperator(postfix.dequeue().name());
                Token operandR = eval.pop();
                Token operandL = eval.pop();
                double result = operator.call(operandL, operandR);
                eval.push(new Token(result));
            }
        }
        if (eval.top().isVariable()) {
            return symbols.get(eval.pop().name());
        } else {
            return eval.pop().value();
        }
    }

    /**
     * Returns a {@code BinaryOperator} which, when called on two operands, applies the appropriate operation to them
     * @param token The string representation of the operator, eg. "+" or "*"
     * @return a {@code BinaryOperator} which implements the appropriate operation
     */
    BinaryOperator getBinaryOperator(String token) {
        switch (token) {
            case "+":
                return (op1, op2) -> op1.value() + op2.value();
            case "*":
                return (op1, op2) -> op1.value() * op2.value();
            case "/":
                return (op1, op2) -> op1.value() / op2.value();
            case "=":
                return (op1, op2) -> {
                    symbols.add(op1.name(), op2.value());
                    return op2.value();
                };
        }
        return null;
    }

    /**
     * Returns a {@code UnaryOperator} which, when called on one operand, applies the appropriate operation to it
     * @param token The string representation of the operator, eg. "sin" or "abs"
     * @return a {@code UnaryOperator} which implements the appropriate operation
     */
    UnaryOperator getUnaryOperator(String token) {
        switch (token) {
            case "-":
                return operand -> -1 * operand.value();
            case "sin":
                return operand -> Math.sin(operand.value());
            case "cos":
                return operand -> Math.cos(operand.value());
            case "tan":
                return operand -> Math.tan(operand.value());
            case "cot":
                return operand -> 1.0 / Math.tan(operand.value());
            case "sec":
                return operand -> 1.0 / Math.cos(operand.value());
            case "csc":
                return operand -> 1.0 / Math.sin(operand.value());
            case "abs":
                return operand -> Math.abs(operand.value());
            case "sqrt":
                return operand -> Math.sqrt(operand.value());
        }
        return null;
    }

    private interface BinaryOperator {
        double call(Token op1, Token op2);
    }

    private interface UnaryOperator {
        double call(Token operand);
    }
}
