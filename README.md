## Amazon Q for Eclipse

> [!IMPORTANT]
> **Amazon Q Developer IDE plugins: end of support**
>
> On April 30, 2027, AWS will discontinue support for Amazon Q Developer IDE plugins. For capabilities similar to Amazon Q Developer IDE plugins, [explore Kiro](https://kiro.dev) to access the latest models and features, including agentic coding, chat and MCP support.
>
> [Learn more](https://aws.amazon.com/blogs/devops/amazon-q-developer-end-of-support-announcement/)

Amazon Q Developer is an advanced AI-powered coding assistant designed to enhance developer productivity and streamline software development processes.

### Key Features

1. **Intelligent Code Generation & Assistance**
   - Explains code and answers software development questions
   - Generates real-time code suggestions from snippets to full functions
   - Provides inline code suggestions based on your comments and existing code
   - Supports contextual conversations about your code

2. **AI-Powered Development Agents**
   - Automates complex, multistep tasks including:
     - Unit testing
     - Documentation
     - Code reviews
   - Assists with implementing features, documenting code, and bootstrapping new projects
   - Achieved highest scores on the SWE-Bench Leaderboard and Leaderboard Lite

3. **Secure Private Repository Integration**
   - Connects securely to your private repositories
   - Customizes and generates more relevant code recommendations
   - Enables querying about your company-specific code
   - Accelerates understanding of internal code bases

4. **Code Reference Log**
   - Attributes code suggestions that are similar to training data
   - Automatically logs accepted code suggestions that match training data
   - Maintains transparency in AI-generated code origins

Amazon Q Developer is designed to be your intelligent coding companion, helping you write better code faster and understand complex codebases more efficiently.

### Available Plans

1. [Free Tier](https://aws.amazon.com/q/developer/getting-started/)
    - Code faster with code suggestions in the IDE
    - Review code licenses with reference tracking
    - Limited monthly access to advanced features:
        - Chat, debug code, add tests, and more in your IDE (50 interactions/month)
        - Amazon Q Developer agents for software development (10 uses/month)
        - Amazon Q Developer Agent for code transformation (1,000 lines of submitted code/month)
        - Answers about your AWS account resources (25 queries/month)

2. [Pro Tier](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/q-pro-tier-setting-up-access.html)
    - Manage users and policies with enterprise access controls
    - Customize Amazon Q to your code base for enhanced suggestions
    - Increased limits on advanced features:
        - Unlimited chat, debugging, and testing in your IDE
        - Unlimited use of Amazon Q Developer agents for software development
        - Unlimited answers about your AWS account resources
    - Additional enterprise-grade features

Both tiers continue to receive updates and improvements.

### Installation Instructions

#### Option 1: Install via Eclipse Marketplace (Recommended)
1. **Open Eclipse Marketplace**
   - Launch Eclipse IDE
   - Navigate to `Help > Eclipse Marketplace...`
   - Search for "Amazon Q"
   - Click "Install" on the Amazon Q plugin

2. **Complete Installation**
   - Review the installation details
   - Accept the license agreement
   - Click "Finish"
   - When prompted, restart Eclipse to complete the installation

![Install-Using-Marketplace](https://github.com/user-attachments/assets/dcb77afd-4ee0-4da7-adb0-dbc22cd80f70)

#### Option 2: Install via Update Site
1. **Open Install Dialog**
   - Launch Eclipse IDE
   - Navigate to `Help > Install New Software...`

2. **Add Amazon Q Repository**
   - Click "Add..." button
   - Enter the following details:
     - Name: `Amazon Q for Eclipse`
     - URL: `https://amazonq.eclipsetoolkit.amazonwebservices.com/`
   - Click "Add"
   - Select "software.aws.toolkits.eclipse" from the available software list
   - Click "Next"

3. **Complete Installation**
   - Review the installation details
   - Accept the license agreement
   - Click "Finish"
   - When prompted, restart Eclipse to complete the installation

![Install-Using-Update-Site](https://github.com/user-attachments/assets/3d8e0667-a405-4daf-9736-dbcc254a3344)

### Demos & Examples

#### Code Context
![Explaining-A-Class](https://github.com/user-attachments/assets/86ff704b-8be1-41e0-be91-fc172b40478f)

#### Generate Tests
![Test-Generation](https://github.com/user-attachments/assets/8f3edc09-6981-4bb2-b0bd-0201e6b73cf1)

#### Create Documentation
![Inline-Chat](https://github.com/user-attachments/assets/ee867bcf-ef62-4468-86d3-1df53ddabf1b)

#### Code Completion
![Code-Completion-Example](https://github.com/user-attachments/assets/30b76708-1cd4-4cd1-9abe-22c9a4a6a8bc)

## License

This project is licensed under the Apache-2.0 License.
